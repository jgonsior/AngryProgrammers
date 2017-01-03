package ab.demo.DAO;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

/**
 * @author: Julius Gonsior
 */
public interface MovesDAO {
    @SqlUpdate("CREATE TABLE IF NOT EXISTS moves (ID SERIAL PRIMARY KEY, gameId INT REFERENCES games(ID), birdNumber INT, fromState VARCHAR(8000), action INT, toState VARCHAR(8000), reward DOUBLE PRECISION, randomAction boolean, trajectoryType VARCHAR(4))")
    void createMovesTable();


    @SqlUpdate("INSERT INTO moves (gameId, birdNumber, fromState, action, toState, reward, randomAction, trajectoryType) VALUES (:gameId, :birdNumber, :fromState, :action, :toState, :reward, :randAction, :trajectoryType);")
    void saveMove(@Bind("gameId") int gameId, @Bind("birdNumber") int birdNumber, @Bind("fromState") int fromState, @Bind("action") int action, @Bind("toState") int toState, @Bind("reward") double reward, @Bind("randAction") boolean randomAction, @Bind("trajectoryType") String trajectoryType);

    /**
     * closes the connection
     */
    void close();
}
