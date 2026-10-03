import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sailpoint.object.Application;
import sailpoint.object.Identity;
import sailpoint.server.IdnRuleUtil;
import sailpoint.tools.GeneralException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Direct unit tests for {@link UsernameGenerator}, covering the generation
 * branches the BeanShell-driven test does not reach.
 */
public class UsernameGeneratorDirectTest {

    private UsernameGenerator generator;
    private IdnRuleUtil idn;

    @BeforeEach
    public void setUp() {
        generator = new UsernameGenerator();
        generator.identity = mock(Identity.class);
        generator.application = mock(Application.class);
        generator.idn = mock(IdnRuleUtil.class);
        when(generator.application.getName()).thenReturn("Active Directory [source]");
        when(generator.identity.getStringAttribute("otherName")).thenReturn(null);
        idn = generator.idn;
    }

    @Test
    public void returnsNullWhenFirstNameMissing() throws GeneralException {
        assertNull(generator.generateUsername(null, "Smith"));
    }

    @Test
    public void returnsNullWhenLastNameMissing() throws GeneralException {
        assertNull(generator.generateUsername("Tyler", null));
    }

    @Test
    public void generatesLowercaseFirstDotLast() throws GeneralException {
        when(idn.accountExistsByDisplayName(anyString(), anyString())).thenReturn(false);
        assertEquals("tyler.smith", generator.generateUsername("Tyler", "Smith"));
    }

    @Test
    public void stripsNonAlphanumericCharacters() throws GeneralException {
        when(idn.accountExistsByDisplayName(anyString(), anyString())).thenReturn(false);
        assertEquals("tylr.smth", generator.generateUsername("Tylér", "Smíth-ß"));
    }

    @Test
    public void truncatesLongNamesToMaxLength() throws GeneralException {
        when(idn.accountExistsByDisplayName(anyString(), anyString())).thenReturn(false);
        // firstName + "." + lastName exceeds 12 chars, so it falls back to
        // first 10 chars of firstName + "." + first letter of lastName.
        assertEquals("alexandert.s",
                generator.generateUsername("AlexanderTheGreat", "Stevenson"));
    }

    @Test
    public void iteratesUntilUsernameIsUnique() throws GeneralException {
        // "john.smith", "john.s" and "john.m" are taken; "john.i" is available.
        // The retry loop appends successive letters of the last name.
        when(idn.accountExistsByDisplayName(anyString(), anyString())).thenAnswer(invocation -> {
            String username = invocation.getArgument(1);
            return "john.smith".equals(username) || "john.s".equals(username) || "john.m".equals(username);
        });
        assertEquals("john.i", generator.generateUsername("John", "Smith"));
    }
}
