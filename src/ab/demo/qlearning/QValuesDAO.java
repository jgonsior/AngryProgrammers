package ab.demo.qlearning;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;

/**
 * @author jgonsior
 */
public interface QValuesDAO {

    @SqlUpdate("CREATE TABLE IF NOT EXISTS q_values (q_value DOUBLE PRECISION, state VARCHAR(8000), action INT, PRIMARY KEY(state, action))")
    void createQValuesTable();

    @SqlUpdate("CREATE TABLE IF NOT EXISTS games (ID SERIAL, level INT, proxyPort INT, expl DOUBLE PRECISION, learn DOUBLE PRECISION, disc DOUBLE PRECISION)")
    void createAllGamesTable();

    @SqlUpdate("CREATE TABLE IF NOT EXISTS moves (ID SERIAL, gameId INT, birdNumber INT, fromState VARCHAR(8000), action INT, toState VARCHAR(8000), reward DOUBLE PRECISION, randomAction boolean, lowTrajectory boolean)")
    void createAllMovesTable();

    @SqlUpdate("INSERT INTO moves (gameId, birdNumber, fromState, action, toState, reward, randomAction, lowTrajectory) VALUES (:gameId, :birdNumber, :fromState, :action, :toState, :reward, :randAction, :lowTrajectory);")
    void saveMove(@Bind("gameId") int gameId,@Bind("birdNumber") int birdNumber,@Bind("fromState") String fromState,@Bind("action") int action,@Bind("toState") String toState,@Bind("reward") double reward,@Bind("randAction") boolean randomAction, @Bind("lowTrajectory") boolean lowTrajectory);

    @SqlUpdate("INSERT INTO games (level, proxyPort, expl, learn, disc) VALUES (:level, :proxyPort, :expl, :learn, :disc)")
    @GetGeneratedKeys
    int saveGame(@Bind("level") int level,@Bind("proxyPort") int proxyPort,@Bind("expl") double expl,@Bind("learn") double learn,@Bind("disc") double disc);

    @SqlUpdate("UPDATE q_values SET q_value=:q_value WHERE state=:state AND action=:action;")
    void updateQValue(@Bind("q_value") double qValue, @Bind("state") String state, @Bind("action") int action);

    @SqlUpdate("INSERT INTO q_values(q_value, state, action) VALUES (:q_value, :state, :action);")
    void insertNewAction(@Bind("q_value") double qValue, @Bind("state") String state, @Bind("action") int action);

    @SqlQuery("SELECT q_value FROM q_values WHERE state=:state AND action=:action;")
    double getQValue(@Bind("state") String state, @Bind("action") int action);

    @SqlQuery("SELECT MAX(q_value) FROM q_values WHERE state=:state;")
    double getHighestQValue(@Bind("state") String state);

    @SqlQuery("SELECT action FROM q_values WHERE state=:state ORDER BY q_value DESC LIMIT 1;")
    int getBestAction(@Bind("state") String state);

    @SqlQuery("SELECT action FROM q_values WHERE state=:state ORDER BY RANDOM() LIMIT 1;")
    int getRandomAction(@Bind("state") String state);

    @SqlQuery("SELECT Count(action) as amount FROM q_values WHERE state=:state;")
    int getActionAmount(@Bind("state") String state);

    /**
     * closes the connection
     */
    void close();
}
