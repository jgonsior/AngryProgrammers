package ab.demo.DAO;

import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

/**
 * @author: Julius Gonsior
 */
public interface ProblemStatesDAO {
    @SqlUpdate("CREATE TABLE IF NOT EXISTS states (ID SERIAL PRIMARY KEY)")
    void createTable();

    @SqlUpdate("INSERT INTO states (ID) VALUES (DEFAULT)")
    @GetGeneratedKeys
    int insertId();

    /**
     * closes the connection
     */
    void close();
}
