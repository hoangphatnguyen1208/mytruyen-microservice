package online.mytruyen.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import static online.mytruyen.identity.Contracts.*;

@Repository
public class IdentityStore {
    final JdbcTemplate db;
    public IdentityStore(JdbcTemplate db) { this.db = db; }
    public JdbcTemplate jdbc() { return db; }
    public List<String> roles(UUID id) {
        return db.queryForList("SELECT r.code FROM roles r JOIN user_roles ur ON r.id=ur.role_id WHERE ur.user_id=? ORDER BY r.id", String.class, id);
    }
    public UserView user(UUID id) {
        var rows = db.query("SELECT * FROM users WHERE id=? AND deleted_at IS NULL",
            (rs,n) -> new UserView(rs.getObject("id", UUID.class), rs.getString("email"),
                rs.getString("username"), rs.getString("full_name"), rs.getBoolean("is_active"),
                List.of(), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), id);
        if (rows.isEmpty()) throw new ApiError(404, "User not found");
        var u=rows.get(0);
        return new UserView(u.id(),u.email(),u.username(),u.full_name(),u.is_active(),roles(id),u.created_at(),u.updated_at());
    }
    public void lock(UUID id) {
        if (db.queryForList("SELECT id FROM users WHERE id=? AND deleted_at IS NULL FOR UPDATE", UUID.class,id).isEmpty())
            throw new ApiError(404,"User not found");
    }
    public void revoke(UUID userId) {
        db.update("UPDATE auth_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE user_id=? AND revoked_at IS NULL",userId);
    }
    public void event(UUID id, String type) {
        // No credentials or profile PII in integration events.
        Long version=db.queryForObject("SELECT version FROM users WHERE id=?",Long.class,id);
        db.update("INSERT INTO outbox_events(event_id,aggregate_id,aggregate_version,event_type,payload) VALUES (?,?,?,?,?)",
            UUID.randomUUID(),id,version,type,"{\"user_id\":\""+id+"\"}");
    }
    public void bump(UUID id) {
        db.update("UPDATE users SET version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",id);
    }
    public boolean activeSession(UUID userId, UUID sessionId) {
        return Boolean.TRUE.equals(db.queryForObject(
            "SELECT COUNT(*) > 0 FROM auth_sessions s JOIN users u ON u.id=s.user_id WHERE s.id=? AND u.id=? AND u.is_active=TRUE AND u.deleted_at IS NULL AND s.revoked_at IS NULL AND s.expires_at>CURRENT_TIMESTAMP",
            Boolean.class,sessionId,userId));
    }
}
