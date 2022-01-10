package org.breedinginsight.brapi.v2;

import lombok.SneakyThrows;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapi.v2.dao.ProgramCache;
import org.junit.jupiter.api.*;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.*;
import java.util.concurrent.Callable;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ProgramCacheUnitTest {

    // TODO: Tests
    // POSTing skips refresh if one is already queued
    // POSTing refresh tracking separate for each program
    // Cache refresh failure invalidates cache

    Integer fetchCount = 0;
    Integer waitTime = 1000;
    Map<UUID, List<BrAPIGermplasm>> mockBrAPI = new HashMap<>();

    @BeforeAll
    void setup() {}

    @AfterEach
    @SneakyThrows
    void setupNextTest() {
        // There is some thread sleeping used in this testing, wait for all processes to clean out between tests
        Thread.sleep(5000);
        fetchCount = 0;
        mockBrAPI = new HashMap<>();
    }

    @SneakyThrows
    public List<BrAPIGermplasm> mockFetch(UUID programId, Integer sleepTime) {
        fetchCount += 1;
        Thread.sleep(sleepTime);
        return mockBrAPI.containsKey(programId) ? new ArrayList<>(mockBrAPI.get(programId)) : new ArrayList<>();
    }

    @SneakyThrows
    public List<BrAPIGermplasm> mockPost(UUID programId, List<BrAPIGermplasm> germplasm) {
        if (!mockBrAPI.containsKey(programId)) {
            mockBrAPI.put(programId, germplasm);
        } else {
            List<BrAPIGermplasm> allGermplasm = mockBrAPI.get(programId);
            allGermplasm.addAll(germplasm);
        }
        return germplasm;
    }

    @Test
    @SneakyThrows
    public void populatedRefreshQueueSkipsRefresh() {
        // Make a lot of post calls and just how many times the fetch method is called
        ProgramCache<List<BrAPIGermplasm>> cache = new ProgramCache<>((Object id) -> mockFetch((UUID) id, waitTime));
        UUID programId = UUID.randomUUID();
        Integer numPost = 10;
        Integer currPost = 0;
        while (currPost < numPost) {
            List<BrAPIGermplasm> newList = new ArrayList<>();
            newList.add(new BrAPIGermplasm());
            cache.post(programId, () -> mockPost(programId, new ArrayList<>(newList)));
            currPost += 1;
        }
        assertTrue(fetchCount < numPost, "A fetch call was made for every post. It shouldn't.");
        assertEquals(1, mockBrAPI.size(), "More than one program existed in mocked brapi db.");
        assertEquals(numPost, mockBrAPI.get(programId).size(), "Wrong number of germplasm in db");
        List<BrAPIGermplasm> cachedGermplasm = cache.get(programId);
        assertEquals(numPost, cachedGermplasm.size(), "Wrong number of germplasm in cache");
    }

    @Test
    @SneakyThrows
    public void programRefreshesSeparated() {
        // Make a lot of post calls on different programs to check that they don't wait for each other
        ProgramCache<List<BrAPIGermplasm>> cache = new ProgramCache<>((Object id) -> mockFetch((UUID) id, waitTime));
        Integer numPost = 10;
        Integer currPost = 0;
        while (currPost < numPost) {
            UUID id = UUID.randomUUID();
            List<BrAPIGermplasm> newList = new ArrayList<>();
            newList.add(new BrAPIGermplasm());
            cache.post(id, () -> mockPost(id, new ArrayList<>(newList)));
            // This doesn't have to do with our code, our mock function is just tripping over itself trying to update the number of fetches
            Thread.sleep(waitTime/5);
            currPost += 1;
        }
        assertEquals(numPost, fetchCount, "A fetch call should have been made for every post");
        assertEquals(numPost, mockBrAPI.size(), "Less programs existed than existed in mock brapi db.");
        for (UUID key: mockBrAPI.keySet()) {
            assertEquals(1, mockBrAPI.get(key).size(), "Wrong number of germplasm in db");
            assertEquals(1, cache.get(key).size(), "Wrong number of germplasm in cache");
        }
    }

    @Test
    @SneakyThrows
    public void initialGetMethodWaitsForLoad() {
        // Test that the get method waits for an ongoing refresh to finish when there isn't any day
        UUID programId = UUID.randomUUID();
        ProgramCache<List<BrAPIGermplasm>> cache = new ProgramCache<>((Object id) -> mockFetch((UUID) id, waitTime), List.of(programId));
        cache.get(programId);
        // Our fetch method should have only been called once for the initial loading
        assertEquals(1, fetchCount, "Fetch method was called on get");
    }

    @Test
    @SneakyThrows
    public void getMethodDoesNotWaitForRefresh() {
        // Test that the get method does not wait for a refresh when there is data present
        UUID programId = UUID.randomUUID();
        List<BrAPIGermplasm> newList = new ArrayList<>();
        newList.add(new BrAPIGermplasm());
        mockBrAPI.put(programId, new ArrayList<>(newList));
        ProgramCache<List<BrAPIGermplasm>> cache = new ProgramCache<>((Object id) -> mockFetch((UUID) id, waitTime), List.of(programId));
        Callable<List<BrAPIGermplasm>> postFunction = () -> mockPost(programId, new ArrayList<>(newList));

        // Get waits for initial fetch
        List<BrAPIGermplasm> cachedGermplasm = cache.get(programId);
        assertEquals(1, cachedGermplasm.size(), "Initial germplasm not as expected");

        // Now post another object and call get immediately to see that it returns the old data
        // TODO: This seems to be waiting
        cache.post(programId, postFunction);
        cachedGermplasm = cache.get(programId);
        assertEquals(1, cachedGermplasm.size(), "Get method seemed to have waited for refresh method");

        // Now wait for the fetch after the post to finish
        Thread.sleep(waitTime*2);
        cachedGermplasm = cache.get(programId);
        assertEquals(2, cachedGermplasm.size(), "Get method did not get updated germplasm");
    }

    public void refreshErrorInvalidatesCache() {
        // Tests that data is invalidated when a refresh method fails and that the data is no longer retrievable
        // TODO: Our get method needs to call update cache in the case of missing data
    }


}
