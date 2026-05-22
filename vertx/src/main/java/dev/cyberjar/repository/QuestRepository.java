package dev.cyberjar.repository;

import dev.cyberjar.domain.Difficulty;
import dev.cyberjar.domain.Quest;
import dev.cyberjar.domain.QuestStatus;
import dev.cyberjar.exception.QuestAlreadyAssignedException;
import dev.cyberjar.exception.QuestNotFoundException;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.util.ArrayList;
import java.util.List;

public class QuestRepository {

    private final Pool pool;

    public QuestRepository(Pool pool) {
        this.pool = pool;
    }

    public Future<Quest> findById(Long id) {
        return pool.preparedQuery("""
                select id, title, difficulty, reward, required_class, status, assigned_hero
                from quests
                where id = $1
                """)
                .execute(Tuple.of(id))
                .map(rows -> rows.iterator().hasNext() ? map(rows.iterator().next()) : null);
    }

    public Future<List<Quest>> search(Difficulty difficulty, String requiredClass) {
        String sql;
        Tuple params;

        if (difficulty != null && requiredClass != null) {
            sql = """
                    select id, title, difficulty, reward, required_class, status, assigned_hero
                    from quests
                    where difficulty = $1
                      and lower(required_class) = lower($2)
                    order by id
                    """;
            params = Tuple.of(difficulty.name(), requiredClass);
        } else if (difficulty != null) {
            sql = """
                    select id, title, difficulty, reward, required_class, status, assigned_hero
                    from quests
                    where difficulty = $1
                    order by id
                    """;
            params = Tuple.of(difficulty.name());
        } else if (requiredClass != null) {
            sql = """
                    select id, title, difficulty, reward, required_class, status, assigned_hero
                    from quests
                    where lower(required_class) = lower($1)
                    order by id
                    """;
            params = Tuple.of(requiredClass);
        } else {
            sql = """
                    select id, title, difficulty, reward, required_class, status, assigned_hero
                    from quests
                    order by id
                    """;
            params = Tuple.tuple();
        }

        return pool.preparedQuery(sql)
                .execute(params)
                .map(rows -> {
                    List<Quest> quests = new ArrayList<>();
                    rows.forEach(row -> quests.add(map(row)));
                    return quests;
                });
    }

    public Future<Quest> create(String title, Difficulty difficulty, int reward, String requiredClass) {
        return pool.preparedQuery("""
                insert into quests (title, difficulty, reward, required_class)
                values ($1, $2, $3, $4)
                returning id, title, difficulty, reward, required_class, status, assigned_hero
                """)
                .execute(Tuple.of(title, difficulty.name(), reward, requiredClass))
                .map(rows -> map(rows.iterator().next()));
    }

    public Future<Quest> update(Long id, String title, Difficulty difficulty, int reward, String requiredClass) {
        return pool.preparedQuery("""
                update quests
                set title = $1,
                    difficulty = $2,
                    reward = $3,
                    required_class = $4
                where id = $5
                returning id, title, difficulty, reward, required_class, status, assigned_hero
                """)
                .execute(Tuple.of(title, difficulty.name(), reward, requiredClass, id))
                .map(rows -> {
                    if (!rows.iterator().hasNext()) {
                        throw new QuestNotFoundException(id);
                    }
                    return map(rows.iterator().next());
                });
    }

    public Future<Void> delete(Long id) {
        return pool.preparedQuery("delete from quests where id = $1")
                .execute(Tuple.of(id))
                .map(rows -> {
                    if (rows.rowCount() == 0) {
                        throw new QuestNotFoundException(id);
                    }
                    return null;
                });
    }

    public Future<Quest> assign(Long id, String heroName) {
        return pool.withTransaction(conn ->
                conn.preparedQuery("""
                        select id, title, difficulty, reward, required_class, status, assigned_hero
                        from quests
                        where id = $1
                        for update
                        """)
                        .execute(Tuple.of(id))
                        .compose(rows -> {
                            if (!rows.iterator().hasNext()) {
                                return Future.failedFuture(new QuestNotFoundException(id));
                            }

                            Quest current = map(rows.iterator().next());

                            if (current.status() != QuestStatus.OPEN) {
                                return Future.failedFuture(new QuestAlreadyAssignedException(id));
                            }

                            return conn.preparedQuery("""
                                    update quests
                                    set status = 'ASSIGNED',
                                        assigned_hero = $1
                                    where id = $2
                                    returning id, title, difficulty, reward, required_class, status, assigned_hero
                                    """)
                                    .execute(Tuple.of(heroName, id))
                                    .map(updated -> map(updated.iterator().next()));
                        })
        );
    }

    private static Quest map(Row row) {
        return new Quest(
                row.getLong("id"),
                row.getString("title"),
                Difficulty.valueOf(row.getString("difficulty")),
                row.getInteger("reward"),
                row.getString("required_class"),
                QuestStatus.valueOf(row.getString("status")),
                row.getString("assigned_hero")
        );
    }
}
