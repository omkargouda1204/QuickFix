"""
app/utils/response_utils.py
Standardised JSON response helpers.
"""
from flask import jsonify


def success(data: dict = None, message: str = "Success", status: int = 200):
    body = {"success": True, "message": message}
    if data is not None:
        body["data"] = data
    return jsonify(body), status


def error(message: str = "An error occurred", status: int = 400, errors: dict = None):
    body = {"success": False, "message": message}
    if errors:
        body["errors"] = errors
    return jsonify(body), status

