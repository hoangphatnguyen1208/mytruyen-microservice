def http_400_username_details(username: str) -> str:
    return f"The username {username} is taken! Be creative and choose another one!"

def http_400_email_details(email: str) -> str:
    return f"The email {email} is already registered! Be creative and choose another one!"

def http_400_signup_credentials_details() -> str:
    return "Signup failed! Recheck all your credentials!"

def http_400_sigin_credentials_details() -> str:
    return "Signin failed! Recheck all your credentials!"

def http_400_book_details(string: str) -> str:
    return f"The book with `{string}` already exists!"

def http_400_genre_details(string: str) -> str:
    return f"The genre with `{string}` already exists!"

def http_400_chapter_details(string: str) -> str:
    return f"The chapter with `{string}` already exists!"

def http_400_chapter_content_details(string: str) -> str:
    return f"The chapter content with `{string}` already exists!"

def http_400_tag_details(string: str) -> str:
    return f"The tag with `{string}` already exists!"

def http_400_author_details(string: str) -> str:
    return f"The author with `{string}` already exists!"

def http_400_status_details(string: str) -> str:
    return f"The status with `{string}` already exists!"

def http_401_unauthorized_details() -> str:
    return "Refused to complete request due to lack of valid authentication!"


def http_403_forbidden_details() -> str:
    return "Refused access to the requested resource!"


def http_404_id_details(id: int) -> str:
    return f"Either the account with id `{id}` doesn't exist, has been deleted, or you are not authorized!"


def http_404_username_details(username: str) -> str:
    return f"Either the account with username `{username}` doesn't exist, has been deleted, or you are not authorized!"


def http_404_email_details(email: str) -> str:
    return f"Either the account with email `{email}` doesn't exist, has been deleted, or you are not authorized!"

def http_404_book_details(string: str) -> str:
    return f"The book with `{string}` doesn't exist"

def http_404_genre_details(genre_id: str) -> str:
    return f"The genre with `{genre_id}` doesn't exist"

def http_404_chapter_details(string: str) -> str:
    return f"The chapter with `{string}` doesn't exist"

def http_404_chapter_content_details(string: str) -> str:
    return f"The chapter content for chapter with `{string}` doesn't exist"

def http_404_tag_details(string: str) -> str:
    return f"The tag with `{string}` doesn't exist"

def http_404_author_details(string: str) -> str:
    return f"The author with `{string}` doesn't exist"

def http_404_status_details(string: str) -> str:
    return f"The status with `{string}` doesn't exist"
