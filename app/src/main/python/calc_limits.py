"""Request-scoped app capacity limits, separate from mathematical validation."""
from contextlib import contextmanager
from contextvars import ContextVar

_removed = ContextVar("remove_computation_limit", default=False)


def limits_removed():
    return _removed.get()


def within_limit(value, maximum):
    return limits_removed() or value <= maximum


def capped(value, maximum):
    return value if limits_removed() else min(value, maximum)


@contextmanager
def computation_limits(removed):
    token = _removed.set(removed is True)
    try:
        yield
    finally:
        _removed.reset(token)
