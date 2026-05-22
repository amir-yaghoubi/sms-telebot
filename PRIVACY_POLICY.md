# Privacy Policy

This Privacy Policy explains how the **SMS Telebot** application handles data processing, permissions, local storage, and security considerations.

## Data Processing

* **No Developer Access:** The application does not collect or transmit any personal data, SMS messages, call logs, passwords, or authentication tokens to the developer or any unauthorized remote servers. The developer has no access to any data processed by the application.

* **User-Directed Transmission:** Data transmission is initiated strictly based on the forwarding rules defined by the user. Depending on the configuration, data is transmitted directly via HTTPS to the official Telegram Bot API endpoint or via secure protocols to the user-specified SMTP email server.

* **Storage and Encryption:** All configuration settings are stored locally on the device. Sensitive data, including Telegram bot tokens and SMTP passwords, is stored in encrypted form.

## Permissions

* **On-Demand Permissions:** The application does not request high-level system permissions upon initial startup. Permissions (such as SMS or Phone access) are requested contextually and strictly when the user activates a feature that requires them.

## Security

* **Antivirus False Positives:** Due to the application's core functionality (monitoring background events and forwarding data), certain automated security scanners may flag the package with generic heuristic detections. These are false positives triggered by the automation capabilities of the software. The complete source code is open and available for independent security audits.
