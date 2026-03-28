"""Tests for Actix-web and Axum web framework detector."""

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.rust.actix_web import ActixWebDetector
from osscodeiq.models.graph import NodeKind


def _ctx(content: str, path: str = "main.rs", language: str = "rust") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestActixWebDetector:
    def setup_method(self):
        self.detector = ActixWebDetector()

    # --- Positive tests: Actix ---

    def test_detects_actix_get(self):
        source = """\
use actix_web::{get, HttpResponse};

#[get("/hello")]
async fn hello() -> HttpResponse {
    HttpResponse::Ok().body("Hello!")
}
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["http_method"] == "GET"
        assert endpoints[0].properties["path"] == "/hello"
        assert endpoints[0].properties["framework"] == "actix_web"
        assert endpoints[0].fqn == "hello"

    def test_detects_actix_post(self):
        source = """\
#[post("/users")]
async fn create_user(body: web::Json<User>) -> HttpResponse {
    HttpResponse::Created().json(body.into_inner())
}
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["http_method"] == "POST"
        assert endpoints[0].properties["path"] == "/users"

    def test_detects_actix_put_delete(self):
        source = """\
#[put("/items/{id}")]
async fn update_item(path: web::Path<u32>) -> HttpResponse {
    HttpResponse::Ok().finish()
}

#[delete("/items/{id}")]
async fn delete_item(path: web::Path<u32>) -> HttpResponse {
    HttpResponse::NoContent().finish()
}
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 2
        methods = {n.properties["http_method"] for n in endpoints}
        assert methods == {"PUT", "DELETE"}

    def test_detects_http_server(self):
        source = """\
#[actix_web::main]
async fn main() -> std::io::Result<()> {
    HttpServer::new(|| {
        App::new().service(hello)
    })
    .bind("127.0.0.1:8080")?
    .run()
    .await
}
"""
        result = self.detector.detect(_ctx(source))
        server_nodes = [n for n in result.nodes if n.kind == NodeKind.MODULE]
        assert len(server_nodes) >= 1
        # Should find both HttpServer and #[actix_web::main]
        labels = {n.label for n in server_nodes}
        assert "HttpServer" in labels

    def test_detects_route_with_web_get(self):
        source = """\
fn config(cfg: &mut web::ServiceConfig) {
    cfg.route("/health", web::get().to(health_check));
}
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["http_method"] == "GET"
        assert endpoints[0].properties["path"] == "/health"
        assert endpoints[0].properties["handler"] == "health_check"

    def test_detects_service_resource(self):
        source = """\
App::new()
    .service(web::resource("/api/items"))
"""
        result = self.detector.detect(_ctx(source))
        resource_nodes = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(resource_nodes) == 1
        assert resource_nodes[0].properties["path"] == "/api/items"

    def test_detects_actix_web_main_attr(self):
        source = """\
#[actix_web::main]
async fn main() -> std::io::Result<()> {
    Ok(())
}
"""
        result = self.detector.detect(_ctx(source))
        main_nodes = [n for n in result.nodes if "#[actix_web::main]" in n.annotations]
        assert len(main_nodes) == 1
        assert main_nodes[0].kind == NodeKind.MODULE

    def test_detects_tokio_main_attr(self):
        source = """\
#[tokio::main]
async fn main() {
    println!("server starting");
}
"""
        result = self.detector.detect(_ctx(source))
        main_nodes = [n for n in result.nodes if "#[tokio::main]" in n.annotations]
        assert len(main_nodes) == 1

    # --- Positive tests: Axum ---

    def test_detects_axum_route(self):
        source = """\
use axum::{Router, routing::get};

let app = Router::new()
    .route("/hello", get(hello_handler));
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["http_method"] == "GET"
        assert endpoints[0].properties["path"] == "/hello"
        assert endpoints[0].properties["framework"] == "axum"
        assert endpoints[0].properties["handler"] == "hello_handler"

    def test_detects_axum_multiple_routes(self):
        source = """\
let app = Router::new()
    .route("/users", get(list_users))
    .route("/users", post(create_user))
    .route("/users/:id", delete(delete_user));
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 3
        methods = {n.properties["http_method"] for n in endpoints}
        assert methods == {"GET", "POST", "DELETE"}

    def test_detects_axum_layer(self):
        source = """\
let app = Router::new()
    .route("/api", get(handler))
    .layer(CorsLayer);
"""
        result = self.detector.detect(_ctx(source))
        middleware = [n for n in result.nodes if n.kind == NodeKind.MIDDLEWARE]
        assert len(middleware) == 1
        assert middleware[0].properties["middleware"] == "CorsLayer"
        assert middleware[0].properties["framework"] == "axum"

    def test_detects_mixed_actix_patterns(self):
        source = """\
use actix_web::{get, post, HttpServer, App};

#[get("/")]
async fn index() -> HttpResponse {
    HttpResponse::Ok().body("index")
}

#[post("/submit")]
async fn submit() -> HttpResponse {
    HttpResponse::Ok().finish()
}

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    HttpServer::new(|| {
        App::new().service(index).service(submit)
    })
    .bind("0.0.0.0:8080")?
    .run()
    .await
}
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) >= 2
        modules = [n for n in result.nodes if n.kind == NodeKind.MODULE]
        assert len(modules) >= 1

    # --- Negative tests ---

    def test_empty_file_returns_nothing(self):
        result = self.detector.detect(_ctx("fn main() {}"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_plain_rust_not_detected(self):
        source = """\
struct Point {
    x: f64,
    y: f64,
}

impl Point {
    fn distance(&self, other: &Point) -> f64 {
        ((self.x - other.x).powi(2) + (self.y - other.y).powi(2)).sqrt()
    }
}
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0

    def test_rocket_not_detected(self):
        source = """\
#[macro_use] extern crate rocket;

#[rocket::get("/hello")]
fn hello() -> &'static str {
    "Hello, world!"
}
"""
        result = self.detector.detect(_ctx(source))
        # rocket::get is not matched by our actix pattern #[get("/path")]
        # because of the rocket:: prefix
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 0

    # --- Determinism test ---

    def test_determinism(self):
        source = """\
use actix_web::{get, post, HttpServer};

#[get("/api/items")]
async fn list_items() -> HttpResponse {
    HttpResponse::Ok().finish()
}

#[post("/api/items")]
async fn create_item() -> HttpResponse {
    HttpResponse::Created().finish()
}

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    HttpServer::new(|| App::new())
        .bind("0.0.0.0:8080")?
        .run()
        .await
}
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)
