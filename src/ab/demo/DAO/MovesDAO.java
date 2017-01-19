package ab.demo.DAO;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

/**
 * @author: Julius Gonsior
 */
public interface MovesDAO {
    @SqlUpdate("CREATE TABLE IF NOT EXISTS moves (ID SERIAL PRIMARY KEY, gameId INT REFERENCES games(ID), birdNumber INT, fromState VARCHAR(8000), targetObjectType VARCHAR(22), aboveCount INT, leftCount INT, rightCount INT, belowCount INT, distanceToPig DOUBLE PRECISION, toState VARCHAR(8000), reward DOUBLE PRECISION, randomAction BOOLEAN, trajectoryType VARCHAR(4))")
    void createTable();


    @SqlUpdate("INSERT INTO moves (gameId, birdNumber, fromState, targetObjectType, aboveCount, leftCount, rightCount, belowCount, distanceToPig, trajectoryType, toState, reward, randomAction) VALUES (:gameId, :birdNumber, :fromState, :targetObjectType, :aboveCount, :leftCount, :rightCount, :belowCount, :distanceToPig, :trajectoryType, :toState, :reward, :randAction);")
    void save(
            @Bind("gameId") int gameId,
            @Bind("birdNumber") int birdNumber,
            @Bind("fromState") int fromState,
            @Bind("targetObjectType") String targetObjectType,
            @Bind("aboveCount") int aboveCount,
            @Bind("leftCount") int leftCount,
            @Bind("rightCount") int rightCount,
            @Bind("belowCount") int belowCount,
            @Bind("distanceToPig") double distanceToPig,
            @Bind("trajectoryType") String trajectoryType,
            @Bind("toState") int toState,
            @Bind("reward") double reward,
            @Bind("randAction") boolean randomAction
    );

    /**
     * closes the connection
     */
    void close();
}
