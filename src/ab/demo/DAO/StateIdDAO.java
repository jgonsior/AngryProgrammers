package ab.demo.DAO;

import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

/**
 * @author: Julius Gonsior
 */
public interface StateIdDAO {
    @SqlUpdate("CREATE TABLE IF NOT EXISTS stateIds (ID SERIAL PRIMARY KEY)")
    void createStateIdsTable();

    @SqlUpdate("INSERT INTO stateIds (ID) VALUES (DEFAULT)")
    @GetGeneratedKeys
    int insertStateId();

    /**
     * closes the connection
     */
    void close();
}
