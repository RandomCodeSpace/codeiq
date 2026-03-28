"""Tests for raw SQL query detector."""

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.java.raw_sql import RawSqlDetector
from osscodeiq.models.graph import NodeKind


def _ctx(content: str, path: str = "UserRepository.java", language: str = "java") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestRawSqlDetector:
    def setup_method(self):
        self.detector = RawSqlDetector()

    def test_detects_query_annotation(self):
        source = """\
public class UserRepository {

    @Query("SELECT u FROM User u WHERE u.email = ?1")
    User findByEmail(String email);
}
"""
        result = self.detector.detect(_ctx(source))
        queries = [n for n in result.nodes if n.kind == NodeKind.QUERY]
        assert len(queries) == 1
        assert "SELECT" in queries[0].properties["query"]
        assert queries[0].properties["source"] == "annotation"
        assert "@Query" in queries[0].annotations

    def test_detects_native_query(self):
        source = """\
public class OrderRepository {

    @Query(value = "SELECT * FROM orders WHERE status = ?1", nativeQuery = true)
    List<Order> findByStatus(String status);
}
"""
        result = self.detector.detect(_ctx(source))
        queries = [n for n in result.nodes if n.kind == NodeKind.QUERY]
        assert len(queries) == 1
        assert queries[0].properties["native"] is True
        assert "orders" in queries[0].properties["tables"]

    def test_detects_jdbc_template(self):
        source = """\
public class UserDao {

    private final JdbcTemplate jdbcTemplate;

    public List<User> findActive() {
        return jdbcTemplate.query("SELECT * FROM users WHERE active = true", new UserMapper());
    }
}
"""
        result = self.detector.detect(_ctx(source))
        queries = [n for n in result.nodes if n.kind == NodeKind.QUERY]
        assert len(queries) == 1
        assert queries[0].properties["source"] == "jdbc_template"
        assert "users" in queries[0].properties["tables"]

    def test_detects_entity_manager_query(self):
        source = """\
public class ReportService {

    private EntityManager entityManager;

    public List<Report> getReports() {
        return entityManager.createNativeQuery("SELECT r.* FROM reports r JOIN users u ON r.user_id = u.id").getResultList();
    }
}
"""
        result = self.detector.detect(_ctx(source))
        queries = [n for n in result.nodes if n.kind == NodeKind.QUERY]
        assert len(queries) == 1
        assert "reports" in queries[0].properties["tables"]

    def test_extracts_table_references(self):
        source = """\
public class AnalyticsDao {

    @Query("SELECT a FROM analytics a JOIN events e ON a.event_id = e.id WHERE a.date > ?1")
    List<Analytics> findRecent(LocalDate since);
}
"""
        result = self.detector.detect(_ctx(source))
        queries = [n for n in result.nodes if n.kind == NodeKind.QUERY]
        assert len(queries) == 1

    def test_empty_returns_nothing(self):
        result = self.detector.detect(_ctx("public class PlainService { }"))
        assert len(result.nodes) == 0

    def test_no_sql_patterns(self):
        source = """\
public class UserService {
    public User getUser(Long id) { return repo.findById(id); }
}
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0

    def test_determinism(self):
        source = """\
public class DataRepo {

    @Query("SELECT d FROM Data d WHERE d.key = ?1")
    Data findByKey(String key);

    public void insert() {
        jdbcTemplate.update("INSERT INTO data (key, val) VALUES (?, ?)", k, v);
    }
}
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
