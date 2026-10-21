package com.umdc.smartkin.quiz;

import com.umdc.smartkin.api.v1.to.QuizAnswerRequest;
import com.umdc.smartkin.mongo.document.LessonDocument.Option;
import com.umdc.smartkin.mongo.document.LessonDocument.Question;
import com.umdc.smartkin.mongo.document.LessonDocument.Quiz;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuizGraderTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    private final QuizGrader grader = new QuizGrader();

    private static Question single(String id, int points) {
        return new Question(id, "s1", "SINGLE_CHOICE", "?", List.of(new Option(id + "a", "wrong", false), new Option(id + "b", "right", true)),
                points, "feedback " + id);
    }

    private static Question multi(String id, int points) {
        return new Question(id, "s1", "MULTIPLE_CHOICE", "?", List.of(new Option(id + "a", "yes", true), new Option(id + "b", "yes too", true),
                new Option(id + "c", "no", false)), points, null);
    }

    private static QuizAnswerRequest answer(String questionId, String... options) {
        return new QuizAnswerRequest(questionId, List.of(options));
    }

    @Test
    void awardsAllThePointsWhenEverythingIsRight() {
        var quiz = new Quiz(70, 3, List.of(single("q1", 10), multi("q2", 20)));

        var graded = grader.grade(quiz, List.of(answer("q1", "q1b"), answer("q2", "q2a", "q2b")), NOW);

        assertEquals(30, graded.score());
        assertEquals(30, graded.maxScore());
        assertEquals(100, graded.percentage());
        assertTrue(graded.passed());
        assertTrue(graded.answers().stream().allMatch(a -> a.correct() && a.answeredAt().equals(NOW)));
    }

    @Test
    void aMultipleChoiceQuestionNeedsTheExactSetWithNoPartialCredit() {
        var quiz = new Quiz(50, 3, List.of(multi("q1", 10)));

        var missingOne = grader.grade(quiz, List.of(answer("q1", "q1a")), NOW);
        var extraOne = grader.grade(quiz, List.of(answer("q1", "q1a", "q1b", "q1c")), NOW);
        var exact = grader.grade(quiz, List.of(answer("q1", "q1b", "q1a")), NOW);

        assertEquals(0, missingOne.score());
        assertFalse(missingOne.answers().get(0).correct());
        assertEquals(0, extraOne.score());
        assertEquals(10, exact.score());
    }

    @Test
    void unansweredQuestionsEarnNothingAndStillAppearInQuizOrder() {
        var quiz = new Quiz(50, 3, List.of(single("q1", 10), single("q2", 10), single("q3", 10)));

        var graded = grader.grade(quiz, List.of(answer("q2", "q2b")), NOW);

        assertEquals(10, graded.score());
        assertEquals(List.of("q1", "q2", "q3"), graded.answers().stream().map(a -> a.questionId()).toList());
        assertEquals(List.of(), graded.answers().get(0).selectedOptionIds());
        assertFalse(graded.answers().get(0).correct());
        assertEquals(10, graded.answers().get(1).pointsAwarded());
        assertEquals(List.of("q2b"), graded.answers().get(1).selectedOptionIds());
    }

    @Test
    void thePercentageIsRoundedDownAndThePassMarkIsInclusive() {
        var quiz = new Quiz(66, 3, List.of(single("q1", 1), single("q2", 1), single("q3", 1)));
        var strict = new Quiz(67, 3, quiz.questions());
        var answers = List.of(answer("q1", "q1b"), answer("q2", "q2b"), answer("q3", "q3a"));

        var atMark = grader.grade(quiz, answers, NOW);
        var belowMark = grader.grade(strict, answers, NOW);

        assertEquals(66, atMark.percentage());
        assertTrue(atMark.passed());
        assertFalse(belowMark.passed());
    }

    @Test
    void aQuizWorthNoPointsScoresZeroPercent() {
        var quiz = new Quiz(0, 3, List.of(single("q1", 0)));
        var strict = new Quiz(1, 3, quiz.questions());

        assertEquals(0, grader.grade(quiz, List.of(answer("q1", "q1b")), NOW).percentage());
        assertTrue(grader.grade(quiz, List.of(), NOW).passed());
        assertFalse(grader.grade(strict, List.of(), NOW).passed());
    }

    @Test
    void aQuestionWithoutCorrectOptionsNeverEarnsPoints() {
        var broken = new Question("q1", "s1", "SINGLE_CHOICE", "?", List.of(new Option("q1a", "a", false)), 10, null);

        var graded = grader.grade(new Quiz(0, 3, List.of(broken)), List.of(new QuizAnswerRequest("q1", List.of())), NOW);

        assertEquals(0, graded.score());
        assertFalse(graded.answers().get(0).correct());
    }

    @Test
    void rejectsAnswersThatDoNotMatchTheQuiz() {
        var quiz = new Quiz(50, 3, List.of(single("q1", 10)));

        var unknownQuestion = assertThrows(InvalidQuizAnswersException.class, () -> grader.grade(quiz, List.of(answer("zz", "q1a")), NOW));
        var twice = assertThrows(InvalidQuizAnswersException.class, () -> grader.grade(quiz, List.of(answer("q1", "q1a"), answer("q1", "q1b")), NOW));
        var unknownOption = assertThrows(InvalidQuizAnswersException.class, () -> grader.grade(quiz, List.of(answer("q1", "nope")), NOW));

        assertTrue(unknownQuestion.getMessage().contains("zz"));
        assertTrue(twice.getMessage().contains("q1"));
        assertTrue(unknownOption.getMessage().contains("q1"));
    }
}
