from sqlmodel.ext.asyncio.session import AsyncSession
from sqlmodel import select

from app.models import Tag
from app.schema.tag import TagCreate

async def create_tag(session: AsyncSession, tag_create: TagCreate) -> Tag:
    db_tag = Tag.model_validate(tag_create)
    session.add(db_tag)
    await session.commit()
    await session.refresh(db_tag)
    return db_tag

async def get_tag_by_id(session: AsyncSession, tag_id: int) -> Tag | None:
    tag = await session.get(Tag, tag_id)
    return tag

async def get_tag_by_name(session: AsyncSession, name: str) -> Tag | None:
    tag = await session.exec(select(Tag).where(Tag.name == name))
    return tag.first()

async def get_tag_by_slug(session: AsyncSession, slug: str) -> Tag | None:
    tag = await session.exec(select(Tag).where(Tag.slug == slug))
    return tag.first()

async def get_tags(session: AsyncSession) -> list[Tag]:
    statement = select(Tag)
    tags = await session.exec(statement)
    return tags.all()

async def update_tag(session: AsyncSession, tag_id: int, tag: TagCreate) -> Tag:
    tag_data = tag.model_dump()
    tag_data = {k: v for k, v in tag_data.items() if k in tag.model_fields_set}
    current_tag = await get_tag_by_id(session, tag_id)
    current_tag.sqlmodel_update(tag_data)
    session.add(current_tag)
    await session.commit()
    await session.refresh(current_tag)
    return current_tag

async def delete_tag(session: AsyncSession, tag_id: int) -> bool:
    tag = await get_tag_by_id(session, tag_id)
    await session.delete(tag)
    await session.commit()
    return True

