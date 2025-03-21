package vacademy.io.assessment_service.features.question_core.enums;

public enum QuestionResponseTypes {
    OPTION,
    ONE_WORD,
    LONG_ANSWER,
    SINGLE_DIGIT_NON_NEGATIVE_INTEGER, // 0 - 9 integers
    INTEGER, // positive and negative integers
    POSITIVE_INTEGER, // all positive integers
    DECIMAL; // fractional number with decimal values
}
