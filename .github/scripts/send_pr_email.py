import os
import smtplib
import ssl
from email.message import EmailMessage
from pathlib import Path


def required_environment(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value


def main() -> None:
    smtp_host = required_environment("SMTP_HOST")
    smtp_port = int(required_environment("SMTP_PORT"))
    smtp_username = required_environment("SMTP_USERNAME")
    smtp_password = required_environment("SMTP_PASSWORD").replace(" ", "")
    recipient = required_environment("EMAIL_TO")
    pr_title = required_environment("PR_TITLE")
    pr_url = required_environment("PR_URL")
    branch_name = required_environment("BRANCH_NAME")
    task_description = required_environment("TASK_DESCRIPTION")
    summary_path = Path(required_environment("CODEX_SUMMARY_PATH"))
    codex_summary = summary_path.read_text(encoding="utf-8").strip()

    message = EmailMessage()
    message["Subject"] = f"[MCP Compass] Pull request ready: {pr_title}"
    message["From"] = smtp_username
    message["To"] = recipient
    message.set_content(
        f"""A single MCP Compass task is complete and ready for manual review.

Task
----
{task_description}

Pull request
------------
{pr_title}
{pr_url}

Branch
------
{branch_name}

Validation
----------
- Backend tests passed.
- Frontend lint passed.
- Frontend production build passed.

Summary
-------
{codex_summary}

The workflow did not merge this pull request and will not start another task.
"""
    )

    context = ssl.create_default_context()
    with smtplib.SMTP_SSL(smtp_host, smtp_port, context=context) as smtp:
        smtp.login(smtp_username, smtp_password)
        smtp.send_message(message)


if __name__ == "__main__":
    main()
