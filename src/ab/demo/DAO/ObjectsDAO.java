package ab.demo.DAO;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

/**
 * @author: Julius Gonsior
 */
public interface ObjectsDAO {
    @SqlUpdate("CREATE TABLE IF NOT EXISTS objects (ID SERIAL, x INT, y INT, type VARCHAR(20), material VARCHAR(20), PRIMARY KEY (x,y,type,material))")
    void createObjectsTable();

    @SqlUpdate("INSERT INTO objects (x, y, type, material) VALUES (:x, :y, :type, :material) ON CONFLICT ON CONSTRAINT objects_pkey DO UPDATE SET x=excluded.x RETURNING ID")
    @GetGeneratedKeys
    int insertObject(@Bind("x") int x, @Bind("y") int y, @Bind("type") String type, @Bind("material") String material);

    /**
     * closes the connection
     */
    void close();
}
