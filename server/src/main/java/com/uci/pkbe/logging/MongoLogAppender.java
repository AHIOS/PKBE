package com.uci.pkbe.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.bson.Document;

/**
 * Writes application logs to MongoDB. Reads {@code MONGODB_URI} itself and does not use a Spring
 * bean, so users, credentials, and ceremonies stay in memory. Idle when the URI is unset.
 */
public class MongoLogAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    static final String COLLECTION = "application_logs";

    private String uri;
    private int ttlDays = 14;

    private MongoClient client;
    private MongoCollection<Document> logs;

    public void setUri(String uri) {
        this.uri = uri;
    }

    public void setTtlDays(int ttlDays) {
        this.ttlDays = ttlDays;
    }

    @Override
    public void start() {
        try {
            connectIfConfigured();
        } catch (RuntimeException e) {
            addError("Mongo log appender disabled", e);
            logs = null;
        }
        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (logs == null || !accept(event)) {
            return;
        }
        Document doc = new Document()
                .append("timestamp", new Date(event.getTimeStamp()))
                .append("level", event.getLevel().toString())
                .append("logger", event.getLoggerName())
                .append("thread", event.getThreadName())
                .append("message", event.getFormattedMessage());
        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            doc.append("throwable", ThrowableProxyUtil.asString(throwable));
        }
        try {
            logs.insertOne(doc);
        } catch (RuntimeException e) {
            addError("Failed to write log to Mongo", e);
        }
    }

    @Override
    public void stop() {
        super.stop();
        if (client != null) {
            client.close();
            client = null;
            logs = null;
        }
    }

    private void connectIfConfigured() {
        String resolved = resolveUri();
        if (resolved.isEmpty()) {
            addInfo("Mongo log appender idle: MONGODB_URI is unset");
            return;
        }
        ConnectionString connection = new ConnectionString(resolved);
        String database = connection.getDatabase();
        if (database == null || database.isBlank()) {
            addError("Mongo log appender disabled: MONGODB_URI must include a database name");
            return;
        }
        int days = resolveTtlDays();
        MongoClient created = MongoClients.create(connection);
        try {
            MongoCollection<Document> collection = created.getDatabase(database).getCollection(COLLECTION);
            collection.createIndex(
                    Indexes.ascending("timestamp"), new IndexOptions().expireAfter((long) days, TimeUnit.DAYS));
            this.client = created;
            this.logs = collection;
            addInfo("Mongo log appender writing to " + database + "." + COLLECTION + " ttlDays=" + days);
        } catch (RuntimeException e) {
            created.close();
            throw e;
        }
    }

    private String resolveUri() {
        if (uri != null && !uri.isBlank()) {
            return uri.trim();
        }
        String env = System.getenv("MONGODB_URI");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String property = System.getProperty("MONGODB_URI");
        return property == null ? "" : property.trim();
    }

    private int resolveTtlDays() {
        String env = System.getenv("PKBE_LOG_TTL_DAYS");
        if (env == null || env.isBlank()) {
            return ttlDays > 0 ? ttlDays : 14;
        }
        try {
            int parsed = Integer.parseInt(env.trim());
            return parsed > 0 ? parsed : 14;
        } catch (NumberFormatException e) {
            addWarn("Ignoring invalid PKBE_LOG_TTL_DAYS=" + env);
            return 14;
        }
    }

    /** Application logs at INFO; everything else only at WARN and above, to spare the free tier. */
    private static boolean accept(ILoggingEvent event) {
        String logger = event.getLoggerName();
        if (logger != null && logger.startsWith("com.uci.pkbe")) {
            return event.getLevel().isGreaterOrEqual(Level.INFO);
        }
        return event.getLevel().isGreaterOrEqual(Level.WARN);
    }
}
