package concurrentjunit47.src.test.java.junit47;

import org.junit.*;


public class BasicTest
{

    private boolean setUpCalled = false;

    private static boolean tearDownCalled = false;

    @Before
    public void setUp()
    {
        setUpCalled = true;
        tearDownCalled = false;
        System.out.println( "Called setUp" );
    }

    @After
    public void tearDown()
    {
        setUpCalled = false;
        tearDownCalled = true;
        System.out.println( "Called tearDown" );
    }

    @Test
    public void testSetUp()
    {
        Assert.assertTrue( "setUp was not called", setUpCalled );
    }

    @AfterClass
    public static void oneTimeTearDown()
    {

    }

}