"""
Main Flask application factory
"""

from flask import Flask
from flask_cors import CORS
from config import config
from models import db
from email_utils import mail
import logging
import os

def create_app(config_name='development'):
    """
    Application factory function

    Args:
        config_name (str): Configuration to use ('development', 'production', 'testing')

    Returns:
        Flask: Configured Flask application
    """
    app = Flask(__name__)

    # Load configuration
    app.config.from_object(config[config_name])

    # Initialize extensions
    db.init_app(app)
    mail.init_app(app)
    CORS(app, resources={r"/api/*": {"origins": "*"}})

    # Register blueprints
    from routes.auth_routes import auth_bp
    from routes.user_routes import user_bp
    app.register_blueprint(auth_bp, url_prefix='/api/auth')
    app.register_blueprint(user_bp, url_prefix='/api/user')

    # Create database tables
    with app.app_context():
        db.create_all()

    # Setup logging
    setup_logging(app)

    # Error handlers
    register_error_handlers(app)

    return app

def setup_logging(app):
    """Setup application logging"""
    if not app.debug:
        if not os.path.exists('logs'):
            os.mkdir('logs')

        from logging.handlers import RotatingFileHandler
        file_handler = RotatingFileHandler(
            'logs/quickfix_backend.log',
            maxBytes=10240000,
            backupCount=10
        )

        formatter = logging.Formatter(
            '%(asctime)s %(levelname)s: %(message)s [in %(pathname)s:%(lineno)d]'
        )
        file_handler.setFormatter(formatter)
        file_handler.setLevel(logging.INFO)
        app.logger.addHandler(file_handler)
        app.logger.setLevel(logging.INFO)

def register_error_handlers(app):
    """Register error handlers"""

    @app.errorhandler(400)
    def bad_request(error):
        return {'message': 'Bad request', 'error': str(error)}, 400

    @app.errorhandler(401)
    def unauthorized(error):
        return {'message': 'Unauthorized', 'error': str(error)}, 401

    @app.errorhandler(403)
    def forbidden(error):
        return {'message': 'Forbidden', 'error': str(error)}, 403

    @app.errorhandler(404)
    def not_found(error):
        return {'message': 'Resource not found', 'error': str(error)}, 404

    @app.errorhandler(500)
    def internal_error(error):
        db.session.rollback()
        return {'message': 'Internal server error', 'error': str(error)}, 500

if __name__ == '__main__':
    app = create_app()
    app.run(host='0.0.0.0', port=5000, debug=True)

