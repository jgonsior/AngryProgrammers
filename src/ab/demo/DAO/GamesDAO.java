package ab.demo.DAO;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

/**
 * @author: Julius Gonsior
 */
public interface GamesDAO {
    @SqlUpdate("CREATE TABLE IF NOT EXISTS games (ID SERIAL PRIMARY KEY, level INT, proxyPort INT, expl DOUBLE PRECISION, learn DOUBLE PRECISION, disc DOUBLE PRECISION)")
    void createGamesTable();

    @SqlUpdate("INSERT INTO games (level, proxyPort, expl, learn, disc) VALUES (:level, :proxyPort, :expl, :learn, :disc)")
    @GetGeneratedKeys
    int saveGame(@Bind("level") int level, @Bind("proxyPort") int proxyPort, @Bind("expl") double expl, @Bind("learn") double learn, @Bind("disc") double disc);

    /**
     * closes the connection
     */
    void close();
}
