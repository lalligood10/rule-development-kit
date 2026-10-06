import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import sailpoint.object.Application;
import sailpoint.object.Identity;
import sailpoint.server.IdnRuleUtil;
import sailpoint.tools.GeneralException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link UsernameGenerator} directly rather than through the BeanShell rule XML.
 * Lives in the default package because the class and its fields are package-private there.
 */
class UsernameGeneratorUnitTest {
    private static final String APP_NAME = "Active Directory [source]";

    private UsernameGenerator generator;
    private IdnRuleUtil idn;
    private Identity identity;

    @BeforeEach
    void setUp() throws GeneralException {
        idn = mock(IdnRuleUtil.class);
        when(idn.accountExistsByDisplayName(anyString(), anyString())).thenReturn(false);

        Application application = mock(Application.class);
        when(application.getName()).thenReturn(APP_NAME);

        // The Identity stub returns "attributeValue" for every attribute, so it must be mocked.
        identity = mock(Identity.class);

        generator = new UsernameGenerator();
        generator.idn = idn;
        generator.application = application;
        generator.identity = identity;
    }

    private void taken(String... usernames) throws GeneralException {
        for (String username : usernames) {
            when(idn.accountExistsByDisplayName(APP_NAME, username)).thenReturn(true);
        }
    }

    @Nested
    class NullHandling {
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t"})
        void missingFirstNameReturnsNull(String firstName) throws GeneralException {
            assertNull(generator.generateUsername(firstName, "Smith"));
            verifyNoInteractions(idn);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t"})
        void missingLastNameReturnsNull(String lastName) throws GeneralException {
            assertNull(generator.generateUsername("Tyler", lastName));
            verifyNoInteractions(idn);
        }

        @Test
        void bothNamesNullReturnsNull() throws GeneralException {
            assertNull(generator.generateUsername(null, null));
            verifyNoInteractions(idn);
        }

        @Test
        void otherNameDoesNotRescueMissingFirstName() throws GeneralException {
            when(identity.getStringAttribute("otherName")).thenReturn("Ty");
            assertNull(generator.generateUsername(null, "Smith"));
        }

        @Test
        void nullOtherNameKeepsFirstName() throws GeneralException {
            when(identity.getStringAttribute("otherName")).thenReturn(null);
            assertEquals("tyler.smith", generator.generateUsername("Tyler", "Smith"));
        }

        @Test
        void otherNameOfOnlySymbolsKeepsFirstName() throws GeneralException {
            when(identity.getStringAttribute("otherName")).thenReturn("--");
            assertEquals("tyler.smith", generator.generateUsername("Tyler", "Smith"));
        }
    }

    @Nested
    class NonAlphanumericStripping {
        @Test
        void stripsPunctuationAndWhitespaceFromBothNames() throws GeneralException {
            assertEquals("maryj.obrien", generator.generateUsername(" Mary J. ", "O'Brien"));
        }

        @Test
        void keepsDigits() throws GeneralException {
            assertEquals("jon2.doe3", generator.generateUsername("Jon2", "Doe-3"));
        }

        @Test
        void dropsAccentedCharactersInsteadOfTransliterating() throws GeneralException {
            // Unlike the rule XML, the Java class does not NFD-normalize first.
            assertEquals("tylr.smith", generator.generateUsername("Tylér", "Smith"));
        }

        @Test
        void otherNameIsStrippedAndReplacesFirstName() throws GeneralException {
            when(identity.getStringAttribute("otherName")).thenReturn("T.J.");
            assertEquals("tj.smith", generator.generateUsername("Tyler", "Smith"));
        }

        @Test
        void stripsBeforeMeasuringLength() throws GeneralException {
            // "Ab-cd-ef.Smith" is 14 chars raw but "abcdef.smith" is 12 after stripping.
            assertEquals("abcdef.smith", generator.generateUsername("Ab-cd-ef", "Smith"));
        }
    }

    @Nested
    class Truncation {
        @Test
        void fullNameOfExactlyTwelveCharsIsKept() throws GeneralException {
            assertEquals("abcde.fghijk", generator.generateUsername("Abcde", "Fghijk"));
        }

        @Test
        void longFullNameWithShortFirstNameUsesLastInitial() throws GeneralException {
            assertEquals("kiefer.s", generator.generateUsername("Kiefer", "Sutherland"));
        }

        @Test
        void firstNameOfExactlyTenCharsIsNotTruncated() throws GeneralException {
            assertEquals("abcdefghij.s", generator.generateUsername("Abcdefghij", "Smith"));
        }

        @Test
        void firstNameLongerThanTenCharsIsTruncatedToTen() throws GeneralException {
            String username = generator.generateUsername("Christopherson", "Lee");
            assertEquals("christophe.l", username);
            assertEquals(12, username.length());
        }

        @Test
        void otherNameIsTruncatedTheSameWay() throws GeneralException {
            when(identity.getStringAttribute("otherName")).thenReturn("Maximilliana");
            assertEquals("maximillia.s", generator.generateUsername("Max", "Smith"));
        }
    }

    @Nested
    class UniquenessRetryLoop {
        @Test
        void returnsFullNameWhenUnique() throws GeneralException {
            assertEquals("jon.doe", generator.generateUsername("Jon", "Doe"));
            verify(idn, times(1)).accountExistsByDisplayName(eq(APP_NAME), anyString());
        }

        @Test
        void shortNameFallsBackThroughEachLastNameLetter() throws GeneralException {
            taken("jon.doe", "jon.d");

            assertEquals("jon.o", generator.generateUsername("Jon", "Doe"));

            InOrder order = inOrder(idn);
            order.verify(idn).accountExistsByDisplayName(APP_NAME, "jon.doe");
            order.verify(idn).accountExistsByDisplayName(APP_NAME, "jon.d");
            order.verify(idn).accountExistsByDisplayName(APP_NAME, "jon.o");
            verify(idn, never()).accountExistsByDisplayName(APP_NAME, "jon.e");
        }

        @Test
        void longNameRetriesWithNextLastNameLetter() throws GeneralException {
            taken("kiefer.s", "kiefer.u");
            assertEquals("kiefer.t", generator.generateUsername("Kiefer", "Sutherland"));
        }

        @Test
        void truncatedNameRetriesWithNextLastNameLetter() throws GeneralException {
            taken("christophe.l");
            assertEquals("christophe.e", generator.generateUsername("Christopherson", "Lee"));
        }

        @Test
        void returnsNullWhenEveryShortNameCandidateIsTaken() throws GeneralException {
            when(idn.accountExistsByDisplayName(anyString(), anyString())).thenReturn(true);

            assertNull(generator.generateUsername("Jon", "Doe"));
            // full name + one attempt per last-name letter
            verify(idn, times(4)).accountExistsByDisplayName(eq(APP_NAME), anyString());
        }

        @Test
        void returnsNullWhenEveryLongNameCandidateIsTaken() throws GeneralException {
            when(idn.accountExistsByDisplayName(anyString(), anyString())).thenReturn(true);

            assertNull(generator.generateUsername("Christopherson", "Lee"));
            verify(idn, times(3)).accountExistsByDisplayName(eq(APP_NAME), anyString());
        }

        @Test
        void isUniqueChecksTheApplicationName() throws GeneralException {
            taken("taken.name");
            assertFalse(generator.isUnique("taken.name"));
            assertTrue(generator.isUnique("free.name"));
            verify(idn).accountExistsByDisplayName(APP_NAME, "free.name");
        }
    }
}
