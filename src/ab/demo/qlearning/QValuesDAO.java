package ab.demo.qlearning;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import java.util.List;

/**
 * @author jgonsior
 */
public interface QValuesDAO {

    @SqlUpdate("CREATE TABLE IF NOT EXISTS q_values (q_value DOUBLE PRECISION, state INT, action INT, PRIMARY KEY(state, action))")
    void createQValuesTable();

    @SqlUpdate("CREATE TABLE IF NOT EXISTS games (ID SERIAL PRIMARY KEY, level INT, proxyPort INT, expl DOUBLE PRECISION, learn DOUBLE PRECISION, disc DOUBLE PRECISION)")
    void createAllGamesTable();

    @SqlUpdate("CREATE TABLE IF NOT EXISTS moves (ID SERIAL PRIMARY KEY, gameId INT REFERENCES games(ID), birdNumber INT, fromState VARCHAR(8000), action INT, toState VARCHAR(8000), reward DOUBLE PRECISION, randomAction boolean, lowTrajectory boolean)")
    void createAllMovesTable();

    @SqlUpdate("CREATE TABLE IF NOT EXISTS objects (ID SERIAL, x INT, y INT, type VARCHAR(20), material VARCHAR(20), PRIMARY KEY (x,y,type,material))")
    void createAllObjectsTable();

    @SqlUpdate("CREATE TABLE IF NOT EXISTS states (stateId INT, objectId INT, PRIMARY KEY (stateId, objectId))")
    void createAllStatesTable();

    @SqlUpdate("CREATE TABLE IF NOT EXISTS stateIds (ID SERIAL PRIMARY KEY)")
    void createAllStateIdsTable();

    @SqlUpdate("INSERT INTO stateIds (ID) VALUES (DEFAULT)")
    @GetGeneratedKeys
    int insertStateId();

    @SqlUpdate("INSERT INTO objects (x, y, type, material) VALUES (:x, :y, :type, :material) ON CONFLICT ON CONSTRAINT objects_pkey DO UPDATE SET x=excluded.x RETURNING ID")
    @GetGeneratedKeys
    int insertObject(@Bind("x") int x, @Bind("y") int y, @Bind("type") String type, @Bind("material") String material);

    @SqlQuery("SELECT objectId FROM states WHERE stateId=:stateId")
    List<String> getObjects(@Bind("stateId") int stateId);

    @SqlQuery("SELECT stateId FROM states WHERE objectId=:objectId")
    List<String> getStates(@Bind("objectId") int objectId);

    @SqlQuery("SELECT stateId, array_agg(objectId) FROM states GROUP BY stateId")
    List<String> getObjectListByStates();

    @SqlUpdate("INSERT INTO states (stateId, objectId) VALUES (:stateId, :objectId) ON CONFLICT ON CONSTRAINT states_pkey DO NOTHING")
    @GetGeneratedKeys
    int insertState(@Bind("stateId") int stateId, @Bind("objectId") int objectId);

    @SqlUpdate("INSERT INTO moves (gameId, birdNumber, fromState, action, toState, reward, randomAction, lowTrajectory) VALUES (:gameId, :birdNumber, :fromState, :action, :toState, :reward, :randAction, :lowTrajectory);")
    void saveMove(@Bind("gameId") int gameId, @Bind("birdNumber") int birdNumber, @Bind("fromState") int fromState, @Bind("action") int action, @Bind("toState") int toState, @Bind("reward") double reward, @Bind("randAction") boolean randomAction, @Bind("lowTrajectory") boolean lowTrajectory);

    @SqlUpdate("INSERT INTO games (level, proxyPort, expl, learn, disc) VALUES (:level, :proxyPort, :expl, :learn, :disc)")
    @GetGeneratedKeys
    int saveGame(@Bind("level") int level, @Bind("proxyPort") int proxyPort, @Bind("expl") double expl, @Bind("learn") double learn, @Bind("disc") double disc);

    @SqlUpdate("UPDATE q_values SET q_value=:q_value WHERE state=:state AND action=:action;")
    void updateQValue(@Bind("q_value") double qValue, @Bind("state") int state, @Bind("action") int action);

    @SqlUpdate("INSERT INTO q_values(q_value, state, action) VALUES (:q_value, :state, :action);")
    void insertNewAction(@Bind("q_value") double qValue, @Bind("state") int state, @Bind("action") int action);

    @SqlQuery("SELECT q_value FROM q_values WHERE state=:state AND action=:action;")
    double getQValue(@Bind("state") int state, @Bind("action") int action);

    @SqlQuery("SELECT MAX(q_value) FROM q_values WHERE state=:state;")
    double getHighestQValue(@Bind("state") int state);

    @SqlQuery("SELECT action FROM q_values WHERE state=:state ORDER BY q_value DESC LIMIT 1;")
    int getBestAction(@Bind("state") int state);

    @SqlQuery("SELECT action FROM q_values WHERE state=:state ORDER BY RANDOM() LIMIT 1;")
    int getRandomAction(@Bind("state") int state);

    @SqlQuery("SELECT Count(action) as amount FROM q_values WHERE state=:state;")
    int getActionAmount(@Bind("state") int state);

    /**
     * closes the connection
     */
    void close();
}