package vacademy.io.assessment_service.features.notification.service;

public class AssessmentNotificaionEmailBody {
    public static final String EMAIL_BODY_WHEN_ASSESSMENT_CREATED = "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <title>Upcoming Assessment Reminder</title>\n" +
            "    <style>\n" +
            "        body {\n" +
            "            font-family: Arial, sans-serif;\n" +
            "            line-height: 1.6;\n" +
            "        }\n" +
            "        .container {\n" +
            "            max-width: 600px;\n" +
            "            margin: 0 auto;\n" +
            "            padding: 20px;\n" +
            "            border-left: 5px solid orange;\n" +
            "        }\n" +
            "        .header {\n" +
            "            font-size: 24px;\n" +
            "            font-weight: bold;\n" +
            "            color: #ff6600;\n" +
            "        }\n" +
            "        .highlight {\n" +
            "            color: orange;\n" +
            "            font-weight: bold;\n" +
            "        }\n" +
            "        .details {\n" +
            "            font-weight: bold;\n" +
            "        }\n" +
            "        .purple-text {\n" +
            "            color: purple;\n" +
            "        }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"container\">\n" +
            "        <p class=\"header\">Upcoming Assessment Reminder</p>\n" +
            "        <p>Dear <span class=\"highlight\">{{learner_name}}</span>,</p>\n" +
            "        <p>Your assessment \"<span class=\"highlight\">{{assessment_name}}</span>\" is scheduled to start in 30 minutes.</p>\n" +
            "        \n" +
            "        <p class=\"details\">Details:</p>\n" +
            "        <ul>\n" +
            "            <li><span class=\"purple-text\">Start Time:</span> {{start_time}}</li>\n" +
            "            <li><span class=\"purple-text\">Duration:</span> {{duration}} minutes</li>\n" +
            "            <li><span class=\"purple-text\">End Time:</span> {{end_time}}</li>\n" +
            "        </ul>\n" +
            "\n" +
            "        <p>Make sure you are prepared and logged in on time.</p>\n" +
            "        <p>Best of luck!</p>\n" +
            "    </div>\n" +
            "</body>\n" +
            "</html>\n";

    public static final String EMAIL_BODY_WHEN_ASSESSMENT_ABOUT_TO_START = "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <title>Upcoming Assessment Reminder</title>\n" +
            "    <style>\n" +
            "        body {\n" +
            "            font-family: Arial, sans-serif;\n" +
            "            line-height: 1.6;\n" +
            "        }\n" +
            "        .container {\n" +
            "            max-width: 600px;\n" +
            "            margin: 0 auto;\n" +
            "            padding: 20px;\n" +
            "            border-left: 5px solid #007bff;\n" +
            "        }\n" +
            "        .header {\n" +
            "            font-size: 24px;\n" +
            "            font-weight: bold;\n" +
            "            color: #007bff;\n" +
            "        }\n" +
            "        .highlight {\n" +
            "            color: #007bff;\n" +
            "            font-weight: bold;\n" +
            "        }\n" +
            "        .details {\n" +
            "            font-weight: bold;\n" +
            "        }\n" +
            "        .purple-text {\n" +
            "            color: purple;\n" +
            "        }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"container\">\n" +
            "        <p class=\"header\">Your Assessment is Starting Soon!</p>\n" +
            "        <p>Dear <span class=\"highlight\">{{learner_name}}</span>,</p>\n" +
            "        <p>Your upcoming assessment \"<span class=\"highlight\">{{assessment_name}}</span>\" is about to begin.</p>\n" +
            "        \n" +
            "        <p class=\"details\">Assessment Details:</p>\n" +
            "        <ul>\n" +
            "            <li><span class=\"purple-text\">Start Time:</span> {{start_time}}</li>\n" +
            "            <li><span class=\"purple-text\">Duration:</span> {{duration}} minutes</li>\n" +
            "            <li><span class=\"purple-text\">End Time:</span> {{end_time}}</li>\n" +
            "        </ul>\n" +
            "\n" +
            "        <p>✅ Please ensure you are logged in and ready before the start time.</p>\n" +
            "        <p>📢 Stay focused, manage your time wisely, and give it your best effort!</p>\n" +
            "        <p>Best wishes,</p>\n" +
            "        <p><strong>Vacademy Team</strong></p>\n" +
            "    </div>\n" +
            "</body>\n" +
            "</html>\n";

    public static final String EMAIL_BODY_WHEN_ASSESSMENT_STARTED = "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <title>Assessment Started Notification</title>\n" +
            "    <style>\n" +
            "        body {\n" +
            "            font-family: Arial, sans-serif;\n" +
            "            line-height: 1.6;\n" +
            "        }\n" +
            "        .container {\n" +
            "            max-width: 600px;\n" +
            "            margin: 0 auto;\n" +
            "            padding: 20px;\n" +
            "            border-left: 5px solid green;\n" +
            "        }\n" +
            "        .header {\n" +
            "            font-size: 24px;\n" +
            "            font-weight: bold;\n" +
            "            color: #28a745;\n" +
            "        }\n" +
            "        .highlight {\n" +
            "            color: green;\n" +
            "            font-weight: bold;\n" +
            "        }\n" +
            "        .details {\n" +
            "            font-weight: bold;\n" +
            "        }\n" +
            "        .purple-text {\n" +
            "            color: purple;\n" +
            "        }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"container\">\n" +
            "        <p class=\"header\">Your Assessment Has Started!</p>\n" +
            "        <p>Dear <span class=\"highlight\">{{learner_name}}</span>,</p>\n" +
            "        <p>Your assessment \"<span class=\"highlight\">{{assessment_name}}</span>\" has just started.</p>\n" +
            "        \n" +
            "        <p class=\"details\">Assessment Details:</p>\n" +
            "        <ul>\n" +
            "            <li><span class=\"purple-text\">Start Time:</span> {{start_time}}</li>\n" +
            "            <li><span class=\"purple-text\">Duration:</span> {{duration}} minutes</li>\n" +
            "            <li><span class=\"purple-text\">End Time:</span> {{end_time}}</li>\n" +
            "        </ul>\n" +
            "\n" +
            "        <p>Make sure to submit your assessment before the deadline.</p>\n" +
            "        <p>Good luck!</p>\n" +
            "    </div>\n" +
            "</body>\n" +
            "</html>\n";



}
