import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import sailpoint.object.Application;
import sailpoint.object.Identity;
import sailpoint.server.IdnRuleUtil;
import sailpoint.tools.GeneralException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UsernameGeneratorUnitTest {
    private static final String APPLICATION_NAME = "Active Directory [source]";

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ", "\t"})
    void returnsNullAndDoesNotCheckUniquenessWhenFirstNameIsMissing(String firstName) throws GeneralException {
        UsernameGenerator generator = generatorWithOtherName("Other");

        assertNull(generator.generateUsername(firstName, "Smith"));

        verifyNoInteractions(generator.idn);
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ", "\t"})
    void returnsNullAndDoesNotCheckUniquenessWhenLastNameIsMissing(String lastName) throws GeneralException {
        UsernameGenerator generator = generatorWithOtherName("Other");

        assertNull(generator.generateUsername("Jane", lastName));

        verifyNoInteractions(generator.idn);
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ", "\t"})
    void ignoresMissingOrBlankOtherName(String otherName) throws GeneralException {
        UsernameGenerator generator = generatorWithOtherName(otherName);

        assertEquals("jane.smith", generator.generateUsername("Jane", "Smith"));
    }

    @Test
    void stripsOtherNameAndUsesItInsteadOfFirstName() throws GeneralException {
        UsernameGenerator generator = generatorWithOtherName("J-ane 2!");

        assertEquals("jane2.smith", generator.generateUsername("Ignored", "Smith"));
    }

    @Test
    void stripsPunctuationAndSpacesButKeepsDigits() throws GeneralException {
        UsernameGenerator generator = generatorWithOtherName(null);

        assertEquals("j0hnp.doe3", generator.generateUsername("J0hn P!", "D'oe 3"));
    }

    @Test
    void dropsNonAsciiLettersInsteadOfTransliteratingThem() throws GeneralException {
        UsernameGenerator generator = generatorWithOtherName(null);

        assertEquals("andr.smith", generator.generateUsername("André", "Smith"));
    }

    @Test
    void keepsUsernameAtTheTwelveCharacterBoundary() throws GeneralException {
        UsernameGenerator generator = generatorWithOtherName(null);

        assertEquals("alice.smiths", generator.generateUsername("Alice", "Smiths"));

        verify(generator.idn).accountExistsByDisplayName(APPLICATION_NAME, "alice.smiths");
    }

    @Test
    void truncatesLongFirstNameToTenCharactersBeforeTheLastNameInitial() throws GeneralException {
        UsernameGenerator generator = generatorWithOtherName(null);

        assertEquals("christophe.l", generator.generateUsername("Christopherson", "Lee"));

        verify(generator.idn).accountExistsByDisplayName(APPLICATION_NAME, "christophe.l");
    }

    @Test
    void retriesShortUsernameCandidatesInOrder() throws GeneralException {
        UsernameGenerator generator = generatorWithOtherName(null);
        when(generator.idn.accountExistsByDisplayName(APPLICATION_NAME, "amy.li")).thenReturn(true);
        when(generator.idn.accountExistsByDisplayName(APPLICATION_NAME, "amy.l")).thenReturn(false);

        assertEquals("amy.l", generator.generateUsername("Amy", "Li"));

        InOrder order = inOrder(generator.idn);
        order.verify(generator.idn).accountExistsByDisplayName(APPLICATION_NAME, "amy.li");
        order.verify(generator.idn).accountExistsByDisplayName(APPLICATION_NAME, "amy.l");
    }

    @Test
    void retriesLongFirstNameCandidatesInOrder() throws GeneralException {
        UsernameGenerator generator = generatorWithOtherName(null);
        when(generator.idn.accountExistsByDisplayName(APPLICATION_NAME, "christophe.l")).thenReturn(true);
        when(generator.idn.accountExistsByDisplayName(APPLICATION_NAME, "christophe.e")).thenReturn(false);

        assertEquals("christophe.e", generator.generateUsername("Christopherson", "Lee"));

        InOrder order = inOrder(generator.idn);
        order.verify(generator.idn).accountExistsByDisplayName(APPLICATION_NAME, "christophe.l");
        order.verify(generator.idn).accountExistsByDisplayName(APPLICATION_NAME, "christophe.e");
    }

    @Test
    void retriesLongLastNameCandidatesInOrder() throws GeneralException {
        UsernameGenerator generator = generatorWithOtherName(null);
        when(generator.idn.accountExistsByDisplayName(APPLICATION_NAME, "alex.a")).thenReturn(true);
        when(generator.idn.accountExistsByDisplayName(APPLICATION_NAME, "alex.n")).thenReturn(false);

        assertEquals("alex.n", generator.generateUsername("Alex", "Anderson"));

        InOrder order = inOrder(generator.idn);
        order.verify(generator.idn).accountExistsByDisplayName(APPLICATION_NAME, "alex.a");
        order.verify(generator.idn).accountExistsByDisplayName(APPLICATION_NAME, "alex.n");
    }

    @Test
    void returnsNullWhenAllShortUsernameCandidatesAreTaken() throws GeneralException {
        UsernameGenerator generator = generatorWithOtherName(null);
        when(generator.idn.accountExistsByDisplayName(APPLICATION_NAME, "amy.li")).thenReturn(true);
        when(generator.idn.accountExistsByDisplayName(APPLICATION_NAME, "amy.l")).thenReturn(true);
        when(generator.idn.accountExistsByDisplayName(APPLICATION_NAME, "amy.i")).thenReturn(true);

        assertNull(generator.generateUsername("Amy", "Li"));

        InOrder order = inOrder(generator.idn);
        order.verify(generator.idn).accountExistsByDisplayName(APPLICATION_NAME, "amy.li");
        order.verify(generator.idn).accountExistsByDisplayName(APPLICATION_NAME, "amy.l");
        order.verify(generator.idn).accountExistsByDisplayName(APPLICATION_NAME, "amy.i");
        verify(generator.idn, times(3)).accountExistsByDisplayName(eq(APPLICATION_NAME), anyString());
    }

    @Test
    void returnsNullWhenAllLongFirstNameCandidatesAreTaken() throws GeneralException {
        UsernameGenerator generator = generatorWithOtherName(null);
        when(generator.idn.accountExistsByDisplayName(eq(APPLICATION_NAME), anyString())).thenReturn(true);
        when(generator.idn.accountExistsByDisplayName(APPLICATION_NAME, "christophe.l")).thenReturn(true);
        when(generator.idn.accountExistsByDisplayName(APPLICATION_NAME, "christophe.e")).thenReturn(true);

        assertNull(generator.generateUsername("Christopherson", "Lee"));

        InOrder order = inOrder(generator.idn);
        order.verify(generator.idn).accountExistsByDisplayName(APPLICATION_NAME, "christophe.l");
        order.verify(generator.idn, times(2)).accountExistsByDisplayName(APPLICATION_NAME, "christophe.e");
        verify(generator.idn, times(3)).accountExistsByDisplayName(eq(APPLICATION_NAME), anyString());
    }

    @Test
    void returnsNullWhenAllLongLastNameCandidatesAreTaken() throws GeneralException {
        UsernameGenerator generator = generatorWithOtherName(null);
        when(generator.idn.accountExistsByDisplayName(eq(APPLICATION_NAME), anyString())).thenReturn(true);

        assertNull(generator.generateUsername("Alex", "Anderson"));

        InOrder order = inOrder(generator.idn);
        order.verify(generator.idn).accountExistsByDisplayName(APPLICATION_NAME, "alex.a");
        order.verify(generator.idn).accountExistsByDisplayName(APPLICATION_NAME, "alex.n");
        order.verify(generator.idn).accountExistsByDisplayName(APPLICATION_NAME, "alex.d");
        order.verify(generator.idn).accountExistsByDisplayName(APPLICATION_NAME, "alex.e");
        order.verify(generator.idn).accountExistsByDisplayName(APPLICATION_NAME, "alex.r");
        order.verify(generator.idn).accountExistsByDisplayName(APPLICATION_NAME, "alex.s");
        order.verify(generator.idn).accountExistsByDisplayName(APPLICATION_NAME, "alex.o");
        order.verify(generator.idn).accountExistsByDisplayName(APPLICATION_NAME, "alex.n");
        verify(generator.idn, times(8)).accountExistsByDisplayName(eq(APPLICATION_NAME), anyString());
    }

    private UsernameGenerator generatorWithOtherName(String otherName) {
        UsernameGenerator generator = new UsernameGenerator();
        generator.idn = mock(IdnRuleUtil.class);
        generator.application = mock(Application.class);
        generator.identity = mock(Identity.class);
        when(generator.application.getName()).thenReturn(APPLICATION_NAME);
        when(generator.identity.getStringAttribute("otherName")).thenReturn(otherName);
        return generator;
    }
}
