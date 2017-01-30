package ab.demo.DAO;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

import java.util.List;

/**
 * @author: Julius Gonsior
 */
public interface ProblemStatesDAO {
    @SqlUpdate("CREATE TABLE IF NOT EXISTS states (ID SERIAL PRIMARY KEY)")
    void createTable();

    @SqlUpdate("CREATE TABLE IF NOT EXISTS objects " +
            "(stateId INT, object VARCHAR(150))")
    void createObjectsTable();

    @SqlUpdate("INSERT INTO states (ID) VALUES (DEFAULT)")
    @GetGeneratedKeys
    int insertId();

    @SqlUpdate("INSERT INTO objects (stateId, object) VALUES (:stateId, :object)")
    void insertObject(@Bind("stateId") int problemStateId, @Bind("object") String object);


    @SqlQuery("SELECT object FROM objects " +
            "WHERE stateId=:stateId")
    List<String> getObjects(@Bind("stateId") int possibleProblemStateId);

    /**
     * closes the connection
     */
    void close();
}
