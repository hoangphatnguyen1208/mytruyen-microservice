"""
Test cases cho CRUD operations
"""
import pytest
from sqlmodel.ext.asyncio.session import AsyncSession
from app.crud import user as user_crud
from app.crud import book as book_crud
from app.crud import genre as genre_crud
from app.schema.auth import UserRegister
from app.schema.book import BookCreate
from app.schema.genre import GenreCreate
from app.models import user_role
import uuid


class TestUserCRUD:
    """Test CRUD operations cho User"""

    @pytest.mark.asyncio
    async def test_create_user(self, db_session: AsyncSession):
        """Test tạo user mới"""
        user_in = UserRegister(
            email="crud_test@example.com",
            password="password123",
        )
        user = await user_crud.create_user(db_session, user_in)
        assert user.email == "crud_test@example.com"
        assert user.role == user_role.USER

    @pytest.mark.asyncio
    async def test_get_user_by_email(self, db_session: AsyncSession, test_user):
        """Test lấy user theo email"""
        user = await user_crud.get_user_by_email(
            db_session, "testuser@example.com"
        )
        assert user is not None
        assert user.email == "testuser@example.com"

    @pytest.mark.asyncio
    async def test_get_user_by_email_not_found(self, db_session: AsyncSession):
        """Test lấy user với email không tồn tại"""
        user = await user_crud.get_user_by_email(
            db_session, "nonexistent@example.com"
        )
        assert user is None

    @pytest.mark.asyncio
    async def test_authenticate_success(self, db_session: AsyncSession, test_user):
        """Test authentication thành công"""
        user = await user_crud.authenticate(
            db_session, "testuser@example.com", "testpassword123"
        )
        assert user is not None
        assert user.email == "testuser@example.com"

    @pytest.mark.asyncio
    async def test_authenticate_wrong_password(
        self, db_session: AsyncSession, test_user
    ):
        """Test authentication với mật khẩu sai"""
        user = await user_crud.authenticate(
            db_session, "testuser@example.com", "wrongpassword"
        )
        assert user is None


class TestBookCRUD:
    """Test CRUD operations cho Book"""

    @pytest.mark.asyncio
    async def test_create_book(self, db_session: AsyncSession, test_admin):
        """Test tạo book mới"""
        book_in = BookCreate(
                    name="CRUD Test Book",  
                    slug="crud-test-book",
                    kind=1, 
                    sex=0, 
                    status_id=1, 
                    synopsis="Test book description",  
                    chapter_per_week=7,  
                    creator_id=test_admin.id,
                    published=True,
                    poster={
                        "poster_default": "http://example.com/poster.jpg",
                        "poster_600": "http://example.com/poster_600.jpg",
                        "poster_300": "http://example.com/poster_300.jpg",
                        "poster_150": "http://example.com/poster_150.jpg"
                    },
                    note="Test note", 
                    genre_ids=[1] 
        )
        book = await book_crud.create_book(db_session, book_in)
        assert book.name == "CRUD Test Book"
        assert book.slug == "crud-test-book"

    @pytest.mark.asyncio
    async def test_get_books(self, db_session: AsyncSession, test_book):
        """Test lấy danh sách books"""
        books = await book_crud.get_books(db_session)
        assert len(books) >= 1
        assert any(book.slug == "test-book" for book in books)

    @pytest.mark.asyncio
    async def test_get_book_by_slug(self, db_session: AsyncSession, test_book):
        """Test lấy book theo slug"""
        book = await book_crud.get_book_by_slug(db_session, "test-book")
        assert book is not None
        assert book.slug == "test-book"
        assert book.name == "Test Book"

    @pytest.mark.asyncio
    async def test_get_book_by_slug_not_found(self, db_session: AsyncSession):
        """Test lấy book với slug không tồn tại"""
        book = await book_crud.get_book_by_slug(db_session, "nonexistent-slug")
        assert book is None

    @pytest.mark.asyncio
    async def test_delete_book(self, db_session: AsyncSession, test_book):
        """Test xóa book"""
        book_id = test_book.id
        result = await book_crud.delete_book(db_session, book_id)
        assert result is True
        
        # Verify book is deleted
        deleted_book = await book_crud.get_book_by_slug(db_session, "test-book")
        assert deleted_book is None


class TestGenreCRUD:
    """Test CRUD operations cho Genre"""

    @pytest.mark.asyncio
    async def test_create_genre(self, db_session: AsyncSession):
        """Test tạo genre mới"""
        genre_in = GenreCreate(
            name="Horror",
            slug="horror",
            description="Horror genre"
        )
        genre = await genre_crud.create_genre(db_session, genre_in)
        assert genre.name == "Horror"
        assert genre.slug == "horror"

    @pytest.mark.asyncio
    async def test_get_genres(self, db_session: AsyncSession, test_genre):
        """Test lấy danh sách genres"""
        genres = await genre_crud.get_genres(db_session)
        assert len(genres) >= 1
        assert any(genre.slug == "fantasy" for genre in genres)

    @pytest.mark.asyncio
    async def test_get_genre_by_slug(self, db_session: AsyncSession, test_genre):
        """Test lấy genre theo slug"""
        genre = await genre_crud.get_genre_by_slug(db_session, "fantasy")
        assert genre is not None
        assert genre.slug == "fantasy"
        assert genre.name == "Fantasy"

    @pytest.mark.asyncio
    async def test_get_genre_by_id(self, db_session: AsyncSession, test_genre):
        """Test lấy genre theo ID"""
        genre = await genre_crud.get_genre_by_id(db_session, (test_genre.id))
        assert genre is not None
        assert genre.id == test_genre.id
        assert genre.name == "Fantasy"
