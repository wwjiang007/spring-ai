/*
 * Copyright 2023-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.transformer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Title extractor with adjacent sharing that uses generative to extract
 * 'section_summary', 'prev_section_summary', 'next_section_summary' metadata fields.
 *
 * @author Christian Tzolov
 */
public class SummaryMetadataEnricher implements DocumentTransformer {

	private static final String SECTION_SUMMARY_METADATA_KEY = "section_summary";

	private static final String NEXT_SECTION_SUMMARY_METADATA_KEY = "next_section_summary";

	private static final String PREV_SECTION_SUMMARY_METADATA_KEY = "prev_section_summary";

	private static final String CONTEXT_STR_PLACEHOLDER = "context_str";

	public static final String DEFAULT_SUMMARY_EXTRACT_TEMPLATE = """
			Here is the content of the section:
			{context_str}

			Summarize the key topics and entities of the section.

			Summary: """;

	public enum SummaryType {

		PREVIOUS, CURRENT, NEXT;

	}

	/**
	 * AI client.
	 */
	private final ChatClient chatClient;

	/**
	 * Number of documents from front to use for title extraction.
	 */
	private final List<SummaryType> summaryTypes;

	private final MetadataMode metadataMode;

	/**
	 * Template for summary extraction.
	 */
	private final String summaryTemplate;

	public SummaryMetadataEnricher(ChatClient chatClient, List<SummaryType> summaryTypes) {
		this(chatClient, summaryTypes, DEFAULT_SUMMARY_EXTRACT_TEMPLATE, MetadataMode.ALL);
	}

	public SummaryMetadataEnricher(ChatClient chatClient, List<SummaryType> summaryTypes, String summaryTemplate,
			MetadataMode metadataMode) {
		Assert.notNull(chatClient, "ChatClient must not be null");
		Assert.hasText(summaryTemplate, "Summary template must not be empty");

		this.chatClient = chatClient;
		this.summaryTypes = CollectionUtils.isEmpty(summaryTypes) ? List.of(SummaryType.CURRENT) : summaryTypes;
		this.metadataMode = metadataMode;
		this.summaryTemplate = summaryTemplate;
	}

	@Override
	public List<Document> apply(List<Document> documents) {

		List<String> documentSummaries = new ArrayList<>();
		for (Document document : documents) {

			var documentContext = document.getFormattedContent(this.metadataMode);

			Prompt prompt = new PromptTemplate(this.summaryTemplate)
				.create(Map.of(CONTEXT_STR_PLACEHOLDER, documentContext));
			documentSummaries.add(this.chatClient.generate(prompt).getGeneration().getOutput().getContent());
		}

		for (int i = 0; i < documentSummaries.size(); i++) {
			Map<String, Object> summaryMetadata = new HashMap<>();
			if (i > 0 && this.summaryTypes.contains(SummaryType.PREVIOUS)) {
				summaryMetadata.put(PREV_SECTION_SUMMARY_METADATA_KEY, documentSummaries.get(i - 1));
			}
			if (i < (documentSummaries.size() - 1) && this.summaryTypes.contains(SummaryType.NEXT)) {
				summaryMetadata.put(NEXT_SECTION_SUMMARY_METADATA_KEY, documentSummaries.get(i + 1));
			}
			if (this.summaryTypes.contains(SummaryType.CURRENT)) {
				summaryMetadata.put(SECTION_SUMMARY_METADATA_KEY, documentSummaries.get(i));
			}

			documents.get(i).getMetadata().putAll(summaryMetadata);
		}

		return documents;
	}

}
