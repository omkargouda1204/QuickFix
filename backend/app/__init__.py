"""
app/__init__.py  —  Application factory
"""
import logging
from flask import Flask
from .config import Config


def create_app(config_class=Config) -> Flask:
    app = Flask(__name__)
    app.config.from_object(config_class)

    # ── Logging ────────────────────────────────────────────────────────
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s  %(levelname)-8s  %(name)s — %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )

    # ── CORS — allow Android devices to reach the API ──────────────────
    try:
        from flask_cors import CORS

        CORS(app, resources={r"/api/*": {"origins": "*"}})
    except ImportError:
        app.logger.warning(
            "flask-cors not installed — cross-origin requests may be blocked. "
            "Run: pip install flask-cors"
        )

    # ── Blueprints ─────────────────────────────────────────────────────
    from .routes.auth_routes import auth_bp

    app.register_blueprint(auth_bp, url_prefix="/api/auth")

    # ── Root health check ──────────────────────────────────────────────
    @app.route("/", methods=["GET"])
    def root_health():
        return {
            "status": "ok",
            "message": "QuickFix backend is running",
            "version": "1.0.0",
        }, 200

    # ── Global error handlers ──────────────────────────────────────────
    @app.errorhandler(404)
    def not_found(e):
        return {"success": False, "message": "Endpoint not found."}, 404

    @app.errorhandler(405)
    def method_not_allowed(e):
        return {"success": False, "message": "Method not allowed."}, 405

    @app.errorhandler(500)
    def internal_error(e):
        app.logger.exception("Unhandled server error: %s", e)
        return {"success": False, "message": "Internal server error."}, 500

    return app

