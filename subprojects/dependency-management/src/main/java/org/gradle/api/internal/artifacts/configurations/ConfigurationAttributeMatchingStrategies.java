/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.artifacts.configurations;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.ConfigurationAttributesMatchingStrategy;
import org.gradle.api.artifacts.ConfigurationAttributeMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class ConfigurationAttributeMatchingStrategies {
    public static <T> List<T> findBestMatches(final ConfigurationAttributesMatchingStrategy strategy, Map<String, String> sourceAttributes, Map<T, Map<String, String>> candidates) {
        Set<String> sourceAttributeNames = sourceAttributes.keySet();
        int attributeCount = sourceAttributeNames.size();
        List<String> requiredAttributes = new ArrayList<String>(attributeCount);
        List<String> optionalAttributes = new ArrayList<String>(attributeCount);
        for (String name : sourceAttributeNames) {
            if (strategy.getAttributeMatcher(name).isRequired()) {
                requiredAttributes.add(name);
            } else {
                optionalAttributes.add(name);
            }
        }

        Map<T, MatchDetails> remainingCandidates = new LinkedHashMap<T, MatchDetails>(candidates.size());
        for (Map.Entry<T, Map<String, String>> entry : candidates.entrySet()) {
            remainingCandidates.put(entry.getKey(), new MatchDetails(entry.getValue()));
        }

        filterCandidates(strategy, sourceAttributes, remainingCandidates, requiredAttributes);
        List<T> singleMatch = findBestMatch(remainingCandidates);
        if (singleMatch!=null) {
            return singleMatch;
        }

        // at this point the list contains all configurations that match exactly or partially all required attributes
        filterCandidates(strategy, sourceAttributes, remainingCandidates, optionalAttributes);
        singleMatch = findBestMatch(remainingCandidates);
        if (singleMatch!=null) {
            return singleMatch;
        }

        return ImmutableList.copyOf(remainingCandidates.keySet());
    }

    private static <T> List<T> findBestMatch(Map<T, MatchDetails> remainingCandidates) {
        if (remainingCandidates.isEmpty()) {
            return Collections.emptyList();
        }
        if (remainingCandidates.size() == 1) {
            return Collections.singletonList(remainingCandidates.keySet().iterator().next());
        }
        int bestScore = Integer.MAX_VALUE;
        int bestCount = 0;
        T best = null;
        for (Map.Entry<T, MatchDetails> entry : remainingCandidates.entrySet()) {
            int score = entry.getValue().score;
            if (score < bestScore) {
                bestScore = score;
                best = entry.getKey();
                bestCount = 1;
            } else if (score == bestScore) {
                bestCount++;
            }
        }
        if (bestCount==1) {
            return Collections.singletonList(best);
        }
        return null;
    }

    private static <T> void filterCandidates(ConfigurationAttributesMatchingStrategy strategy, Map<String, String> sourceAttributes, Map<T, MatchDetails> candidates, List<String> requiredAttributes) {
        for (String requiredAttribute : requiredAttributes) {
            String requestedValue = sourceAttributes.get(requiredAttribute);
            for (Iterator<Map.Entry<T, MatchDetails>> it = candidates.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<T, MatchDetails> entry = it.next();
                MatchDetails details = entry.getValue();
                Map<String, String> candidateAttributes = details.attributes;
                if (!candidateAttributes.containsKey(requiredAttribute)) {
                    it.remove();
                } else {
                    ConfigurationAttributeMatcher matcher = strategy.getAttributeMatcher(requiredAttribute);
                    int cmp = matcher.score(requestedValue, candidateAttributes.get(requiredAttribute));
                    if (cmp<0) {
                        it.remove();
                    }
                    details.score += cmp;
                }
            }
        }
    }

    private static class MatchDetails {
        final Map<String, String> attributes;
        int score;

        private MatchDetails(Map<String, String> attributes) {
            this.attributes = attributes;
        }
    }
}
