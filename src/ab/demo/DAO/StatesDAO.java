package ab.demo.DAO;

import ab.demo.qlearning.StateObject;
import ab.demo.qlearning.StateObjectsMapper;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;

import java.util.List;

/**
 * @author: Julius Gonsior
 */
public interface StatesDAO {
    @SqlUpdate("CREATE TABLE IF NOT EXISTS states (stateId INT, objectId INT, PRIMARY KEY (stateId, objectId))")
    void createStatesTable();

    @SqlQuery("SELECT objectId FROM states WHERE stateId=:stateId")
    List<String> getObjects(@Bind("stateId") int stateId);

    @SqlQuery("SELECT stateId FROM states WHERE objectId=:objectId")
    List<String> getStates(@Bind("objectId") int objectId);

    @Mapper(StateObjectsMapper.class)
    @SqlQuery("SELECT stateId, array_agg(objectId) as objectIds FROM states GROUP BY stateId")
    List<StateObject> getObjectIdsForAllStates();

    @SqlUpdate("INSERT INTO states (stateId, objectId) VALUES (:stateId, :objectId) ON CONFLICT ON CONSTRAINT states_pkey DO NOTHING")
    @GetGeneratedKeys
    int insertState(@Bind("stateId") int stateId, @Bind("objectId") int objectId);

    /**
     * closes the connection
     */
    void close();
}
