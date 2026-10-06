import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import sailpoint.object.Application;
import sailpoint.object.Identity;
import sailpoint.server.IdnRuleUtil;
import sailpoint.tools.GeneralException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsernameGeneratorUnitTest {

    private static final String APP_NAME = "Active Directory [source]";

    private UsernameGenerator generator;
    private IdnRuleUtil idn;

    @BeforeEach
    void setUp() throws GeneralException {
        generator = new UsernameGenerator();

        idn = mock(IdnRuleUtil.class);
        when(idn.accountExistsByDisplayName(any(), any())).thenReturn(false);
        generator.idn = idn;

        Application application = mock(Application.class);
        when(application.getName()).thenReturn(APP_NAME);
        generator.application = application;

        generator.identity = mock(Identity.class);
    }

    private void takenUsernames(String... usernames) throws GeneralException {
        for (String username : usernames) {
            when(idn.accountExistsByDisplayName(APP_NAME, username)).thenReturn(true);
        }
    }

    // --- null handling ---

    @Test
    void returnsNullWhenFirstNameIsNull() throws GeneralException {
        assertNull(generator.generateUsername(null, "Smith"));
        verify(idn, never()).accountExistsByDisplayName(any(), any());
    }

    @Test
    void returnsNullWhenLastNameIsNull() throws GeneralException {
        assertNull(generator.generateUsername("John", null));
        verify(idn, never()).accountExistsByDisplayName(any(), any());
    }

    @Test
    void returnsNullWhenBothNamesAreNull() throws GeneralException {
        assertNull(generator.generateUsername(null, null));
    }

    @Test
    void returnsNullWhenNamesAreBlank() throws GeneralException {
        assertNull(generator.generateUsername("   ", "Smith"));
        assertNull(generator.generateUsername("John", "\t"));
        assertNull(generator.generateUsername("", ""));
    }

    @Test
    void trimsSurroundingWhitespace() throws GeneralException {
        assertEquals("john.smith", generator.generateUsername("  John ", " Smith  "));
    }

    @Test
    void nullOtherNameFallsBackToFirstName() throws GeneralException {
        when(generator.identity.getStringAttribute("otherName")).thenReturn(null);
        assertEquals("john.smith", generator.generateUsername("John", "Smith"));
    }

    @Test
    void emptyOtherNameFallsBackToFirstName() throws GeneralException {
        when(generator.identity.getStringAttribute("otherName")).thenReturn("");
        assertEquals("john.smith", generator.generateUsername("John", "Smith"));
    }

    // --- non-alphanumeric stripping ---

    @Test
    void stripsNonAlphanumericCharactersAndLowercases() throws GeneralException {
        assertEquals("joe.osmth", generator.generateUsername("J-o e", "O'Sm!th"));
    }

    @Test
    void keepsDigits() throws GeneralException {
        assertEquals("ann2.lee3", generator.generateUsername("Ann2", "Lee3"));
    }

    @Test
    void stripsNonAsciiLetters() throws GeneralException {
        assertEquals("tylr.smith", generator.generateUsername("Tylér", "Smith"));
    }

    @Test
    void otherNameReplacesFirstNameAndIsStripped() throws GeneralException {
        when(generator.identity.getStringAttribute("otherName")).thenReturn("B.o-b");
        assertEquals("bob.smith", generator.generateUsername("Robert", "Smith"));
    }

    @Test
    void otherNameOfOnlySymbolsFallsBackToFirstName() throws GeneralException {
        when(generator.identity.getStringAttribute("otherName")).thenReturn("--");
        assertEquals("john.smith", generator.generateUsername("John", "Smith"));
    }

    @Test
    void lengthIsMeasuredAfterStripping() throws GeneralException {
        // "Mary-Ann.Lee-Ho" is 15 chars raw but "maryann.leeho" (13) after stripping; still truncated.
        assertEquals("maryann.l", generator.generateUsername("Mary-Ann", "Lee-Ho"));
        // "Al-ex.Mi-ller" is 13 raw but "alex.miller" (11) after stripping; not truncated.
        assertEquals("alex.miller", generator.generateUsername("Al-ex", "Mi-ller"));
    }

    // --- 12-char truncation path ---

    @Test
    void fullNameOfExactlyMaxLengthIsNotTruncated() throws GeneralException {
        assertEquals("alexand.mill", generator.generateUsername("Alexand", "Mill"));
    }

    @Test
    void fullNameOverMaxLengthUsesFirstNameAndLastInitial() throws GeneralException {
        assertEquals("kiefer.s", generator.generateUsername("Kiefer", "Sutherland"));
    }

    @Test
    void firstNameOfExactlyTenCharsIsKeptWhole() throws GeneralException {
        assertEquals("abcdefghij.s", generator.generateUsername("Abcdefghij", "Smith"));
    }

    @Test
    void firstNameLongerThanTenCharsIsTruncatedToTen() throws GeneralException {
        String username = generator.generateUsername("Christopherson", "Lee");
        assertEquals("christophe.l", username);
        assertEquals(generator.MAX_USERNAME_LENGTH, username.length());
    }

    @Test
    void truncatedUsernameNeverExceedsMaxLength() throws GeneralException {
        String username = generator.generateUsername("Maximilianalexander", "Wolfeschlegelsteinhausen");
        assertEquals("maximilian.w", username);
        assertTrue(username.length() <= generator.MAX_USERNAME_LENGTH);
    }

    // --- uniqueness retry loop ---

    @Test
    void checksUniquenessAgainstApplicationName() throws GeneralException {
        generator.generateUsername("John", "Smith");
        verify(idn).accountExistsByDisplayName(APP_NAME, "john.smith");
    }

    @Test
    void shortNameRetriesWithEachLastNameCharacterWhenTaken() throws GeneralException {
        takenUsernames("john.smith", "john.s", "john.m");

        assertEquals("john.i", generator.generateUsername("John", "Smith"));

        InOrder order = inOrder(idn);
        order.verify(idn).accountExistsByDisplayName(APP_NAME, "john.smith");
        order.verify(idn).accountExistsByDisplayName(APP_NAME, "john.s");
        order.verify(idn).accountExistsByDisplayName(APP_NAME, "john.m");
        order.verify(idn).accountExistsByDisplayName(APP_NAME, "john.i");
        order.verifyNoMoreInteractions();
    }

    @Test
    void longNameRetriesWithNextLastNameCharacter() throws GeneralException {
        takenUsernames("kiefer.s", "kiefer.u");
        assertEquals("kiefer.t", generator.generateUsername("Kiefer", "Sutherland"));
        verify(idn, times(3)).accountExistsByDisplayName(eq(APP_NAME), anyString());
    }

    @Test
    void truncatedFirstNameRetriesWithNextLastNameCharacter() throws GeneralException {
        takenUsernames("christophe.l");
        assertEquals("christophe.e", generator.generateUsername("Christopherson", "Lee"));
    }

    @Test
    void retryCandidatesAreLowercased() throws GeneralException {
        // "John.McDonald" is 13 chars, so this goes through the truncation path.
        takenUsernames("john.m");
        assertEquals("john.c", generator.generateUsername("John", "McDonald"));
    }

    @Test
    void returnsNullWhenEveryShortNameCandidateIsTaken() throws GeneralException {
        when(idn.accountExistsByDisplayName(any(), any())).thenReturn(true);

        assertNull(generator.generateUsername("Al", "Lee"));
        // full name + one attempt per last-name character
        verify(idn, times(4)).accountExistsByDisplayName(eq(APP_NAME), anyString());
    }

    @Test
    void returnsNullWhenEveryLongNameCandidateIsTaken() throws GeneralException {
        when(idn.accountExistsByDisplayName(any(), any())).thenReturn(true);

        assertNull(generator.generateUsername("Christopherson", "Lee"));
        verify(idn, times(3)).accountExistsByDisplayName(eq(APP_NAME), anyString());
    }

    @Test
    void isUniqueReflectsAccountExistence() throws GeneralException {
        takenUsernames("taken");
        assertFalse(generator.isUnique("taken"));
        assertTrue(generator.isUnique("free"));
    }
}
