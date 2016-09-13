/*
 * Hivemall: Hive scalable Machine Learning Library
 *
 * Copyright (C) 2016 Makoto YUI
 * Copyright (C) 2013-2015 National Institute of Advanced Industrial Science and Technology (AIST)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package hivemall.systemtest.runner;

import hivemall.systemtest.exception.QueryExecutionException;
import hivemall.systemtest.model.HQ;
import hivemall.systemtest.model.RawHQ;
import hivemall.systemtest.model.StrictHQ;
import hivemall.systemtest.model.lazy.LazyMatchingResource;
import hivemall.utils.lang.Preconditions;
import org.junit.rules.ExternalResource;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class SystemTestTeam extends ExternalResource {
    @Nonnull
    private final List<SystemTestRunner> runners;
    @Nonnull
    private final List<SystemTestRunner> reachGoal;

    @Nonnull
    private final List<StrictHQ> initHqs;
    @Nonnull
    private final Map<Entry<StrictHQ, String>, Boolean> entries;

    private boolean needRun = false; // remind `run()`

    public SystemTestTeam(final SystemTestRunner... runners) {
        this.runners = new ArrayList<SystemTestRunner>();
        this.reachGoal = new ArrayList<SystemTestRunner>(); // distinct
        this.initHqs = new ArrayList<StrictHQ>();
        this.entries = new LinkedHashMap<Entry<StrictHQ, String>, Boolean>();

        this.runners.addAll(Arrays.asList(runners));
    }

    @Override
    protected void after() {
        if (needRun) {
            throw new IllegalStateException("Call `SystemTestTeam#run()`");
        }

        for (SystemTestRunner runner : reachGoal) {
            try {
                final List<String> tables = runner.exec(HQ.tableList());
                for (String t : tables) {
                    if (!runner.isImmutableTable(t)) {
                        runner.exec(HQ.dropTable(t));
                    }
                }
            } catch (Exception ex) {
                throw new QueryExecutionException("Failed to resetPerMethod database. "
                        + ex.getMessage());
            }
        }
    }

    // add additional runner for each @Test method
    public void add(final SystemTestRunner... runners) {
        this.runners.addAll(Arrays.asList(runners));
    }

    // add initialization for each @Test method
    public void initBy(@Nonnull final StrictHQ hq) {
        initHqs.add(hq);

        needRun = true;
    }

    public void initBy(@Nonnull final List<? extends StrictHQ> hqs) {
        initHqs.addAll(hqs);

        needRun = true;
    }

    public void set(@Nonnull final StrictHQ hq, @CheckForNull final String expected, boolean ordered) {
        Preconditions.checkNotNull(expected);

        entries.put(pair(hq, expected), ordered);

        needRun = true;
    }

    public void set(@Nonnull final StrictHQ hq, @CheckForNull final String expected) {
        Preconditions.checkNotNull(expected);

        entries.put(pair(hq, expected), false);

        needRun = true;
    }

    public void set(@Nonnull final List<? extends StrictHQ> hqs,
            @CheckForNull final List<String> expecteds, @CheckForNull final List<Boolean> ordereds) {
        Preconditions.checkNotNull(expecteds);
        Preconditions.checkNotNull(ordereds);
        Preconditions.checkArgument(hqs.size() == expecteds.size(),
            "Mismatch between number of queries(%s) and length of answers(%s)", hqs.size(),
            expecteds.size());
        Preconditions.checkArgument(hqs.size() == ordereds.size(),
            "Mismatch between number of queries(%s) and correspond ordered flags(%s)", hqs.size(),
            ordereds.size());

        for (int i = 0; i < expecteds.size(); i++) {
            set(hqs.get(i), expecteds.get(i), ordereds.get(i));
        }

        needRun = true;
    }

    public void set(@Nonnull final List<? extends StrictHQ> hqs,
            @CheckForNull final List<String> expecteds) {
        final List<Boolean> ordereds = new ArrayList<Boolean>();
        for (int i = 0; i < hqs.size(); i++) {
            ordereds.add(false);
        }
        set(hqs, expecteds, ordereds);
    }

    public void set(@Nonnull final LazyMatchingResource hq,
            @CheckForNull final SystemTestCommonInfo ci, final boolean ordered) {
        final List<RawHQ> rhqs = hq.toStrict(ci.caseDir);
        final String[] answers = hq.getAnswers(ci.answerDir);

        Preconditions.checkArgument(rhqs.size() == answers.length,
            "Mismatch between number of queries(%s) and length of answers(%s)", rhqs.size(),
            answers.length);

        for (int i = 0; i < answers.length; i++) {
            set(rhqs.get(i), answers[i], ordered);
        }

        needRun = true;
    }

    public void set(@Nonnull final LazyMatchingResource hq, final SystemTestCommonInfo ci) {
        set(hq, ci, false);
    }

    public void run() throws Exception {
        needRun = false;

        if (runners.size() == 0) {
            throw new IllegalStateException("Set at least one runner.");
        }

        for (SystemTestRunner runner : runners) {
            if (!reachGoal.contains(runner)) {
                // initialization each @Test methods
                for (StrictHQ q : initHqs) {
                    runner.exec(q);
                }
                reachGoal.add(runner);
            }

            for (Entry<Entry<StrictHQ, String>, Boolean> entry : entries.entrySet()) {
                runner.matching(entry.getKey().getKey(), entry.getKey().getValue(),
                    entry.getValue());
            }
        }
    }

    private Entry<StrictHQ, String> pair(StrictHQ hq, String answer) {
        return new SimpleEntry<StrictHQ, String>(hq, answer);
    }
}
