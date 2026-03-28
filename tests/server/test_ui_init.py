"""Tests for the OSSCodeIQ UI __init__ module."""

from __future__ import annotations


class TestSetupUiImportable:
    def test_setup_ui_is_callable(self) -> None:
        """Verify setup_ui can be imported and is a callable function."""
        from osscodeiq.server.ui import setup_ui

        assert callable(setup_ui)

    def test_setup_ui_accepts_service_param(self) -> None:
        """Verify setup_ui expects a service parameter (inspect signature)."""
        import inspect

        from osscodeiq.server.ui import setup_ui

        sig = inspect.signature(setup_ui)
        assert "service" in sig.parameters
