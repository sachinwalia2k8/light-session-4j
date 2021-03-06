package com.networknt.session;

import io.undertow.server.session.SessionIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link SessionRepository} backed by a {@link java.util.Map} and that uses a
 * {@link MapSession}. The injected {@link java.util.Map} can be backed by a distributed
 * NoSQL store like Hazelcast, for instance. Note that the supplied map itself is
 * responsible for purging the expired sessions.
 *
 * @author Rob Winch
 */
public class MapSessionRepository implements SessionRepository<MapSession> {
    private static Logger logger = LoggerFactory.getLogger(MapSessionRepository.class);
    /**
     * If non-null, this value is used to override
     * {@link Session#setMaxInactiveInterval(int)}.
     */
    private int defaultMaxInactiveInterval;
    private SessionIdGenerator sessionIdGenerator;

    private final Map<String, Session> sessions = new HashMap<>();

    /**
     * Creates a new instance backed by the provided {@link java.util.Map}.
     *
     */
    public MapSessionRepository(SessionIdGenerator sessionIdGenerator) {
        this.sessionIdGenerator = sessionIdGenerator;
    }

    /**
     * If non-null, this value is used to override
     * {@link Session#setMaxInactiveInterval(int)}.
     * @param defaultMaxInactiveInterval the number of seconds that the {@link Session}
     * should be kept alive between client requests.
     */
    public void setDefaultMaxInactiveInterval(int defaultMaxInactiveInterval) {
        this.defaultMaxInactiveInterval = defaultMaxInactiveInterval;
    }

    @Override
    public void save(MapSession session) {
        if (!session.getId().equals(session.getOriginalId())) {
            this.sessions.remove(session.getOriginalId());
            session.setOriginalId(session.getId());
        }
        this.sessions.put(session.getId(), new MapSession(session));
    }

    @Override
    public MapSession findById(String id) {
        Session saved = this.sessions.get(id);
        if (saved == null) {
            return null;
        }
        if (saved.isExpired()) {
            deleteById(saved.getId());
            return null;
        }
        return (MapSession)saved;
    }

    @Override
    public void deleteById(String id) {
        this.sessions.remove(id);
    }

    @Override
    public MapSession createSession() {
        String sessionId = null;
        int count = 0;
        while (sessionId == null) {
            sessionId = sessionIdGenerator.createSessionId();
            if(sessions.containsKey(sessionId)) {
                sessionId = null;
            }
            if(count++ == 100) {
                //this should never happen
                //but we guard against pathalogical session id generators to prevent an infinite loop
                logger.error("Unable to generate sessionId");
            }
        }

        MapSession result = new MapSession(sessionId);
        sessions.put(sessionId, result);
        if (this.defaultMaxInactiveInterval != 0) {
            result.setMaxInactiveInterval(this.defaultMaxInactiveInterval);
        }
        return result;
    }
}
