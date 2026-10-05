import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import sailpoint.object.Application;
import sailpoint.object.Identity;
import sailpoint.server.IdnRuleUtil;
import sailpoint.tools.GeneralException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class UsernameGeneratorUnitTest {

    private UsernameGenerator generator;
    private IdnRuleUtil idn;
    private Application application;
    private Identity identity;

    @BeforeEach
    public void setUp() throws GeneralException {
        generator = new UsernameGenerator();
        idn = mock(IdnRuleUtil.class);
        application = mock(Application.class);
        when(application.getName()).thenReturn("Active Directory [source]");
        identity = mock(Identity.class);
        generator.idn = idn;
        generator.application = application;
        generator.identity = identity;
    }

    @Test
    public void testNullFirstNameReturnsNull() throws GeneralException {
        assertNull(generator.generateUsername(null, "Smith"));
        verifyNoInteractions(idn);
    }

    @Test
    public void testNullLastNameReturnsNull() throws GeneralException {
        assertNull(generator.generateUsername("Tyler", null));
        verifyNoInteractions(idn);
    }

    @Test
    public void testWhitespaceOnlyFirstNameReturnsNull() throws GeneralException {
        assertNull(generator.generateUsername("   ", "Smith"));
        verifyNoInteractions(idn);
    }

    @Test
    public void testNonAlphanumericCharactersStripped() throws GeneralException {
        when(idn.accountExistsByDisplayName(anyString(), anyString())).thenReturn(false);
        assertEquals("joe.smith", generator.generateUsername("Jo-e", "Sm'ith"));
    }

    @Test
    public void testUsernameIsLowercased() throws GeneralException {
        when(idn.accountExistsByDisplayName(anyString(), anyString())).thenReturn(false);
        assertEquals("tyler.smith", generator.generateUsername("TYLER", "SMITH"));
    }

    @Test
    public void testOtherNameOverridesFirstName() throws GeneralException {
        when(identity.getStringAttribute("otherName")).thenReturn("J.R.");
        when(idn.accountExistsByDisplayName(anyString(), anyString())).thenReturn(false);
        assertEquals("jr.smith", generator.generateUsername("John", "Smith"));
    }

    @Test
    public void testEmptyOtherNameIsIgnored() throws GeneralException {
        when(identity.getStringAttribute("otherName")).thenReturn("");
        when(idn.accountExistsByDisplayName(anyString(), anyString())).thenReturn(false);
        assertEquals("john.smith", generator.generateUsername("John", "Smith"));
    }

    @Test
    public void testLongFirstNameIsTruncated() throws GeneralException {
        when(idn.accountExistsByDisplayName(anyString(), anyString())).thenReturn(false);
        String result = generator.generateUsername("Christopherson", "Lee");
        assertEquals("christophe.l", result);
        assertEquals(12, result.length());
    }

    @Test
    public void testOverMaxLengthWithShortFirstName() throws GeneralException {
        when(idn.accountExistsByDisplayName(anyString(), anyString())).thenReturn(false);
        assertEquals("kiefer.s", generator.generateUsername("Kiefer", "Sutherland"));
    }

    @Test
    public void testUniquenessRetriesInitialsOnShortName() throws GeneralException {
        when(idn.accountExistsByDisplayName(anyString(), anyString()))
                .thenReturn(true).thenReturn(true).thenReturn(false);
        assertEquals("tyler.m", generator.generateUsername("Tyler", "Smith"));
        InOrder inOrder = inOrder(idn);
        inOrder.verify(idn).accountExistsByDisplayName("Active Directory [source]", "tyler.smith");
        inOrder.verify(idn).accountExistsByDisplayName("Active Directory [source]", "tyler.s");
        inOrder.verify(idn).accountExistsByDisplayName("Active Directory [source]", "tyler.m");
    }

    @Test
    public void testUniquenessRetriesOnTruncatedName() throws GeneralException {
        when(idn.accountExistsByDisplayName(anyString(), anyString()))
                .thenReturn(true).thenReturn(false);
        assertEquals("christophe.e", generator.generateUsername("Christopherson", "Lee"));
    }

    @Test
    public void testUniquenessRetriesOnOverMaxName() throws GeneralException {
        when(idn.accountExistsByDisplayName(anyString(), anyString()))
                .thenReturn(true).thenReturn(true).thenReturn(false);
        assertEquals("kiefer.t", generator.generateUsername("Kiefer", "Sutherland"));
    }

    @Test
    public void testReturnsNullWhenAllUsernamesTaken() throws GeneralException {
        when(idn.accountExistsByDisplayName(anyString(), anyString())).thenReturn(true);
        assertNull(generator.generateUsername("Tyler", "Smith"));
        verify(idn, times(6)).accountExistsByDisplayName(anyString(), anyString());
    }
}
