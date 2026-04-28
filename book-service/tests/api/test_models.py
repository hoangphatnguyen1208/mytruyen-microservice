"""
Test cases cho Models
"""
import pytest
from app.models import User, Book, Genre, user_role
from sqlmodel.ext.asyncio.session import AsyncSession
import uuid


class TestModels:
    """Test database models"""

    @pytest.mark.asyncio
    async def test_user_model_with_admin_role(self, db_session: AsyncSession):
        """Test tạo User với role ADMIN"""
        admin = User(
            id=uuid.uuid4(),
            email="admin_model@example.com",
            hashed_password="hashedpassword",
            full_name="Admin Model",
            role=user_role.ADMIN
        )
        db_session.add(admin)
        await db_session.commit()
        await db_session.refresh(admin)
        
        assert admin.role == user_role.ADMIN

    @pytest.mark.asyncio
    async def test_genre_model_creation(self, db_session: AsyncSession):
        """Test tạo Genre model"""
        genre = Genre(
            name="Test Genre",
            slug="test-genre",
            description="Test description"
        )
        db_session.add(genre)
        await db_session.commit()
        await db_session.refresh(genre)
        
        assert genre.name == "Test Genre"
        assert genre.slug == "test-genre"

    @pytest.mark.asyncio
    async def test_book_model_creation(self, db_session: AsyncSession, test_admin):
        """Test tạo Book model"""
        book = Book(
            name="Model Test Book",
            slug="model-test-book",
            description="Test description",
            author_id=test_admin.id,
            creator_id=test_admin.id,
            poster={"url": "http://example.com/poster.jpg"}, 
            published=True,
            note="Test note",
            kind=1, 
            sex=0,
            status_id=1,
            synopsis="Test synopsis"
        )
        db_session.add(book)
        await db_session.commit()
        await db_session.refresh(book)
        
        assert book.name == "Model Test Book"
        assert book.slug == "model-test-book"
        assert book.author_id == test_admin.id

    @pytest.mark.asyncio
    async def test_book_creator_relationship(
        self, db_session: AsyncSession, test_admin, test_book
    ):
        """Test relationship giữa Book và User (creator)"""
        # Load creator relationship
        await db_session.refresh(test_book, ["creator"])
        
        assert test_book.creator is not None
        assert test_book.creator.id == test_admin.id
        assert test_book.creator.email == "admin@example.com"

    @pytest.mark.asyncio
    async def test_user_books_relationship(
        self, db_session: AsyncSession, test_admin, test_book
    ):
        """Test relationship giữa User và Books"""
        # Load books relationship
        await db_session.refresh(test_admin, ["books"])
        
        assert len(test_admin.books) >= 1
        assert any(book.slug == "test-book" for book in test_admin.books)

    @pytest.mark.asyncio
    async def test_user_role_enum(self):
        """Test user_role enum"""
        assert user_role.USER == "user"
        assert user_role.ADMIN == "admin"
