"""init

Revision ID: a46baa154e17
Revises:
Create Date: 2026-03-05 00:32:48.078249

"""

from typing import Sequence, Union

from alembic import op
from sqlmodel import SQLModel

import app.models  # noqa: F401 - registers the book-service tables


# revision identifiers, used by Alembic.
revision: str = "a46baa154e17"
down_revision: Union[str, Sequence[str], None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    SQLModel.metadata.create_all(bind=op.get_bind())


def downgrade() -> None:
    """Downgrade schema."""
    SQLModel.metadata.drop_all(bind=op.get_bind())
