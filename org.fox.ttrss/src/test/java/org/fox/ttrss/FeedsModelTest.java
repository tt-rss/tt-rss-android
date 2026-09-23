package org.fox.ttrss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import android.content.SharedPreferences;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.fox.ttrss.types.Feed;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public class FeedsModelTest {
    @Rule
    public InstantTaskExecutorRule taskExecutorRule = new InstantTaskExecutorRule();

    private Application application;
    private SharedPreferences preferences;

    @Before
    public void setUp() {
        application = mock(Application.class);
        preferences = mock(SharedPreferences.class);
        when(application.getSharedPreferences(anyString(), anyInt())).thenReturn(preferences);
        when(application.getSessionId()).thenReturn("expired-session");
        when(application.getApiLevel()).thenReturn(1);
        when(preferences.getBoolean("expand_special_cat", true)).thenReturn(true);
    }

    @Test
    public void feedsPreserveLoginFailureAndCachedList() {
        assertRequestFailure(new FeedsModel(application), ApiCommon.ApiError.LOGIN_FAILED, 1);
    }

    @Test
    public void specialFeedsFailureStopsRootCategoryRequest() {
        assertRequestFailure(new RootCategoriesModel(application), ApiCommon.ApiError.LOGIN_FAILED, 1);
    }

    @Test
    public void rootCategoriesPreserveLoginFailureWithoutExpandedSpecialFeeds() {
        when(preferences.getBoolean("expand_special_cat", true)).thenReturn(false);
        assertRequestFailure(new RootCategoriesModel(application), ApiCommon.ApiError.LOGIN_FAILED, 1);
    }

    @Test
    public void rootCategoriesPreserveFailureAfterSpecialFeedsSucceed() {
        assertRequestFailure(new RootCategoriesModel(application), ApiCommon.ApiError.LOGIN_FAILED, 2);
    }

    @Test
    public void feedsPreserveNetworkFailureAndCachedList() {
        assertRequestFailure(new FeedsModel(application), ApiCommon.ApiError.IO_ERROR, 1);
    }

    @Test
    public void rootCategoriesPreserveNetworkFailureAndStopLoading() {
        assertRequestFailure(new RootCategoriesModel(application), ApiCommon.ApiError.IO_ERROR, 1);
    }

    @Test
    public void malformedSpecialFeedsStopRootCategoryRequest() {
        FeedsModel model = new RootCategoriesModel(application);
        useImmediateExecutor(model);
        AtomicInteger requests = new AtomicInteger();

        try (MockedStatic<ApiCommon> api = mockStatic(ApiCommon.class)) {
            api.when(() -> ApiCommon.performRequest(eq(application), any(), eq(model)))
                    .thenAnswer(invocation -> {
                        requests.incrementAndGet();
                        model.setLastError(ApiCommon.ApiError.SUCCESS);
                        return new JsonObject();
                    });

            model.startLoading(new Feed(Feed.CAT_SPECIAL, "Root", true));

            assertEquals(1, requests.get());
            assertEquals(ApiCommon.ApiError.OTHER_ERROR, model.getLastError());
            assertFalse(model.getIsLoading().getValue());
        }
    }

    private void assertRequestFailure(FeedsModel model, ApiCommon.ApiError error, int failOnRequest) {
        useImmediateExecutor(model);
        List<Feed> cachedFeeds = List.of(new Feed(42, "Cached feed", false));
        model.m_feeds.setValue(cachedFeeds);
        List<Boolean> loadingStates = new ArrayList<>();
        model.getIsLoading().observeForever(loadingStates::add);
        AtomicInteger feedUpdates = new AtomicInteger();
        model.getFeeds().observeForever(feeds -> feedUpdates.incrementAndGet());
        AtomicInteger requests = new AtomicInteger();

        try (MockedStatic<ApiCommon> api = mockStatic(ApiCommon.class);
             MockedStatic<Application> app = mockStatic(Application.class)) {
            app.when(Application::getInstance).thenReturn(application);
            api.when(() -> ApiCommon.performRequest(eq(application), any(), eq(model)))
                    .thenAnswer(invocation -> {
                        if (requests.incrementAndGet() == failOnRequest) {
                            model.setLastError(error);
                            return null;
                        }
                        model.setLastError(ApiCommon.ApiError.SUCCESS);
                        return new JsonArray();
                    });

            model.startLoading(new Feed(Feed.CAT_SPECIAL, "Root", true));

            assertEquals(failOnRequest, requests.get());
            assertEquals(error, model.getLastError());
            assertNull(model.getLastErrorMessage());
            assertEquals(List.of(false, true, false), loadingStates);
            assertSame(cachedFeeds, model.getFeeds().getValue());
            assertEquals(1, feedUpdates.get());

            // A refresh after reauthentication must be able to publish a new list.
            when(application.getSessionId()).thenReturn("renewed-session");
            model.startLoading(new Feed(Feed.CAT_SPECIAL, "Root", true));

            assertEquals(ApiCommon.ApiError.SUCCESS, model.getLastError());
            assertFalse(model.getIsLoading().getValue());
            assertEquals(2, feedUpdates.get());
            assertEquals(List.of(), model.getFeeds().getValue());
        }
    }

    private void useImmediateExecutor(FeedsModel model) {
        model.m_executor.shutdownNow();
        model.m_executor = mock(ExecutorService.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(model.m_executor).execute(any(Runnable.class));
    }
}
