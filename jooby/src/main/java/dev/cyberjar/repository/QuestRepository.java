package dev.cyberjar.repository;

import dev.cyberjar.domain.Difficulty;
import dev.cyberjar.domain.Quest;
import dev.cyberjar.domain.QuestStatus;
import dev.cyberjar.exception.QuestAlreadyAssignedException;
import dev.cyberjar.exception.QuestNotFoundException;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;

import java.util.List;
import java.util.Optional;

public class QuestRepository {

    private final Jdbi jdbi;

    private static final RowMapper<Quest> QUEST_MAPPER = (rs, ctx) -> new Quest(
            rs.getLong("id"),
            rs.getString("title"),
            Difficulty.valueOf(rs.getString("difficulty")),
            rs.getInt("reward"),
            rs.getString("required_class"),
            QuestStatus.valueOf(rs.getString("status")),
            rs.getString("assigned_hero")
    );

    public QuestRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public Optional<Quest> findById(Long id) {
        return jdbi.withHandle(handle -> handle
                .createQuery("""
                        select id, title, difficulty, reward, required_class, status, assigned_hero
                        from quests
                        where id = :id
                        """)
                .bind("id", id)
                .map(QUEST_MAPPER)
                .findOne());
    }

    public List<Quest> search(Difficulty difficulty, String requiredClass) {
        if (difficulty != null && requiredClass != null) {
            return jdbi.withHandle(handle -> handle
                    .createQuery("""
                            select id, title, difficulty, reward, required_class, status, assigned_hero
                            from quests
                            where difficulty = :difficulty
                              and lower(required_class) = lower(:requiredClass)
                            order by id
                            """)
                    .bind("difficulty", difficulty.name())
                    .bind("requiredClass", requiredClass)
                    .map(QUEST_MAPPER)
                    .list());
        }

        if (difficulty != null) {
            return jdbi.withHandle(handle -> handle
                    .createQuery("""
                            select id, title, difficulty, reward, required_class, status, assigned_hero
                            from quests
                            where difficulty = :difficulty
                            order by id
                            """)
                    .bind("difficulty", difficulty.name())
                    .map(QUEST_MAPPER)
                    .list());
        }

        if (requiredClass != null) {
            return jdbi.withHandle(handle -> handle
                    .createQuery("""
                            select id, title, difficulty, reward, required_class, status, assigned_hero
                            from quests
                            where lower(required_class) = lower(:requiredClass)
                            order by id
                            """)
                    .bind("requiredClass", requiredClass)
                    .map(QUEST_MAPPER)
                    .list());
        }
        return jdbi.withHandle(handle -> handle
                .createQuery("""
                        select id, title, difficulty, reward, required_class, status, assigned_hero
                        from quests
                        order by id
                        """)
                .map(QUEST_MAPPER)
                .list());
    }

    public Quest create(String title, Difficulty difficulty, int reward, String requiredClass) {
        return jdbi.withHandle(handle -> handle
                .createQuery("""
                        insert into quests (title, difficulty, reward, required_class)
                        values (:title, :difficulty, :reward, :requiredClass)
                        returning id, title, difficulty, reward, required_class, status, assigned_hero
                        """)
                .bind("title", title)
                .bind("difficulty", difficulty.name())
                .bind("reward", reward)
                .bind("requiredClass", requiredClass)
                .map(QUEST_MAPPER)
                .one());
    }


    public Quest update(Long id, String title, Difficulty difficulty, int reward, String requiredClass) {
        return jdbi.withHandle(handle -> handle
                .createQuery("""
                        update quests
                        set title = :title,
                            difficulty = :difficulty,
                            reward = :reward,
                            required_class = :requiredClass
                        where id = :id
                        returning id, title, difficulty, reward, required_class, status, assigned_hero
                        """)
                .bind("id", id)
                .bind("title", title)
                .bind("difficulty", difficulty.name())
                .bind("reward", reward)
                .bind("requiredClass", requiredClass)
                .map(QUEST_MAPPER)
                .findOne()
                .orElseThrow(() -> new
                        QuestNotFoundException(id)));
    }

    public void delete(Long id) {
        int deleted = jdbi.withHandle(handle -> handle
                .createUpdate("delete from quests where id = :id")
                .bind("id", id)
                .execute());

        if (deleted == 0) {
            throw new QuestNotFoundException(id);
        }
    }


    public Quest assign(Long id, String heroName) {
        return jdbi.inTransaction(handle -> {
            Quest current = handle
                    .createQuery("""
                            select id, title, difficulty, reward, required_class, status, assigned_hero
                            from quests
                            where id = :id
                            for update
                            """)
                    .bind("id", id)
                    .map(QUEST_MAPPER)
                    .findOne()
                    .orElseThrow(() -> new QuestNotFoundException(id));

            if (current.status() != QuestStatus.OPEN) {
                throw new QuestAlreadyAssignedException(id);
            }

            return handle
                    .createQuery("""
                            update quests
                            set status = 'ASSIGNED',
                                assigned_hero = :heroName
                            where id = :id
                            returning id, title, difficulty, reward, required_class, status, assigned_hero
                            """)
                    .bind("id", id)
                    .bind("heroName", heroName)
                    .map(QUEST_MAPPER)
                    .one();
        });
    }

}
