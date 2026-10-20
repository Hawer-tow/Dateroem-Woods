from fastapi import APIRouter, Header

from app.core import backend_client
from app.schemas.mobile import AiChatMessageRequest

router = APIRouter(prefix="/ai/chat", tags=["ai-chat"])


# "implement the chat bot right below that in the same section to allow
# it to answer questions still role based" - a longer timeout since a
# conversational model call is slower than a normal CRUD request (same
# reasoning as the image-scan proxy's own extended timeout).
@router.post("")
async def send_message(request: AiChatMessageRequest, x_actor_user_id: int = Header(...)):
    return await backend_client.post(
        "/api/ai/chat",
        json=request.model_dump(),
        headers={"X-Actor-User-Id": str(x_actor_user_id)},
        timeout=45.0,
    )


@router.get("/history")
async def history(x_actor_user_id: int = Header(...)):
    return await backend_client.get("/api/ai/chat/history", headers={"X-Actor-User-Id": str(x_actor_user_id)})
