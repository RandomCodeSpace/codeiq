package io.github.randomcodespace.iq.analyzer;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * Pre-scans file content for architecture-relevant keywords before full parsing.
 * <p>
 * Uses fast substring checks (no regex) to determine whether a file is likely
 * to contain architectural patterns — endpoints, services, entities, config,
 * messaging, auth, or infrastructure. Files with no matching keywords are
 * skipped, targeting a ~60-70% skip rate for pure utilities, POJOs, and DTOs.
 */
@Component
public class ArchitectureKeywordFilter {

    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "@Controller", "@RestController", "@Service", "@Component",
            "@Repository", "@Entity", "@Table", "@Bean", "@Configuration",
            "@KafkaListener", "KafkaTemplate", "@RabbitListener", "JmsTemplate",
            "DataSource", "JdbcTemplate", "JpaRepository", "EntityManager",
            "RestTemplate", "WebClient", "FeignClient", "@Scheduled", "@Async",
            "@Cacheable", "@PreAuthorize", "@Secured", "@RolesAllowed",
            "HttpSecurity", "WebSecurityConfigurerAdapter", "@Transactional",
            "@Query", "@EventListener", "ApplicationEvent", "ConnectionFactory",
            "RedisTemplate", "GrpcService"
    );

    private static final Set<String> PYTHON_KEYWORDS = Set.of(
            "FastAPI", "Django", "Flask", "app.route", "@app.get", "@app.post",
            "SQLAlchemy", "create_engine", "models.Model", "celery", "@task",
            "redis", "kafka", "boto3", "httpx", "requests", "APIRouter",
            "Depends", "BaseModel", "SessionLocal", "AsyncSession"
    );

    private static final Set<String> TYPESCRIPT_KEYWORDS = Set.of(
            "@Controller", "@Get", "@Post", "@Injectable", "TypeORM", "Prisma",
            "Sequelize", "Mongoose", "express", "Router", "app.get", "app.post",
            "kafkajs", "amqplib", "ioredis", "bull", "HttpService", "fetch",
            "axios", "@Module", "@Guard", "@Middleware", "Schema", "model("
    );

    private static final Set<String> GO_KEYWORDS = Set.of(
            "http.HandleFunc", "http.Handle", "gin.Default", "mux.Router",
            "echo.New", "sql.Open", "gorm", "sqlx", "sarama", "grpc.NewServer",
            "grpc.Dial", "redis.NewClient", "http.Client"
    );

    private static final Set<String> CSHARP_KEYWORDS = Set.of(
            "[ApiController]", "[HttpGet]", "[HttpPost]", "DbContext",
            "IDbConnection", "[Authorize]", "MassTransit", "IMediator",
            "ILogger", "IHostedService", "BackgroundService"
    );

    private static final Set<String> RUST_KEYWORDS = Set.of(
            "#[get]", "#[post]", "actix_web", "rocket", "axum", "sqlx",
            "diesel", "tokio-postgres", "rdkafka", "redis", "tonic", "reqwest"
    );

    private static final Set<String> RUBY_KEYWORDS = Set.of(
            "ActiveRecord", "has_many", "belongs_to", "Sidekiq", "Redis.new",
            "Faraday", "HTTParty", "Devise", "Pundit"
    );

    private static final Set<String> GENERIC_KEYWORDS = Set.of(
            "import", "require", "from", "endpoint", "route", "router",
            "middleware", "guard", "interceptor", "filter", "handler",
            "listener", "consumer", "producer", "subscriber", "publisher"
    );

    private static final Map<String, Set<String>> LANGUAGE_KEYWORDS = Map.ofEntries(
            Map.entry("java", JAVA_KEYWORDS),
            Map.entry("python", PYTHON_KEYWORDS),
            Map.entry("typescript", TYPESCRIPT_KEYWORDS),
            Map.entry("javascript", TYPESCRIPT_KEYWORDS),
            Map.entry("go", GO_KEYWORDS),
            Map.entry("csharp", CSHARP_KEYWORDS),
            Map.entry("rust", RUST_KEYWORDS),
            Map.entry("ruby", RUBY_KEYWORDS)
    );

    /**
     * Determines whether a file should be analyzed based on its content.
     *
     * @param content  file content as a string
     * @param language language identifier (e.g. "java", "python", "typescript")
     * @return {@code true} if any architecture keyword is found
     */
    public boolean shouldAnalyze(String content, String language) {
        if (content == null || content.isBlank()) {
            return false;
        }

        Set<String> languageSpecific = LANGUAGE_KEYWORDS.get(language != null ? language.toLowerCase() : "");
        if (languageSpecific != null) {
            for (String keyword : languageSpecific) {
                if (content.contains(keyword)) {
                    return true;
                }
            }
        }

        for (String keyword : GENERIC_KEYWORDS) {
            if (content.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines whether a file should be analyzed based on its raw byte content.
     * Decodes bytes as UTF-8 and delegates to {@link #shouldAnalyze(String, String)}.
     *
     * @param rawContent raw file bytes
     * @param language   language identifier
     * @return {@code true} if any architecture keyword is found
     */
    public boolean shouldAnalyze(byte[] rawContent, String language) {
        if (rawContent == null || rawContent.length == 0) {
            return false;
        }
        return shouldAnalyze(new String(rawContent, StandardCharsets.UTF_8), language);
    }
}
