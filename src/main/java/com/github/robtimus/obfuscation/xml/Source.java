/*
 * Source.java
 * Copyright 2022 Rob Spoor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.robtimus.obfuscation.xml;

import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.copyAll;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.support.CountingReader;
import com.github.robtimus.obfuscation.support.ObfuscatorUtils;

interface Source {

    char charAt(int index);

    int length();

    int skipLeadingWhitespace(int fromIndex, int toIndex);

    int skipTrailingWhitespace(int fromIndex, int toIndex);

    void appendTo(int from, int to, Appendable destination) throws IOException;

    void obfuscateText(int from, int to, Obfuscator obfuscator, Appendable destination) throws IOException;

    int appendRemainder(int from, int to, Appendable destination) throws IOException;

    boolean needsTruncating();

    void truncate();

    final class OfCharSequence implements Source {

        private final CharSequence s;

        OfCharSequence(CharSequence s) {
            this.s = s;
        }

        @Override
        public char charAt(int index) {
            return s.charAt(index);
        }

        @Override
        public int length() {
            return s.length();
        }

        @Override
        public int skipLeadingWhitespace(int fromIndex, int toIndex) {
            return ObfuscatorUtils.skipLeadingWhitespace(s, fromIndex, toIndex);
        }

        @Override
        public int skipTrailingWhitespace(int fromIndex, int toIndex) {
            return ObfuscatorUtils.skipTrailingWhitespace(s, fromIndex, toIndex);
        }

        @Override
        public void appendTo(int from, int to, Appendable destination) throws IOException {
            destination.append(s, from, to);
        }

        @Override
        public void obfuscateText(int from, int to, Obfuscator obfuscator, Appendable destination) throws IOException {
            obfuscator.obfuscateText(s, from, to, destination);
        }

        @Override
        public int appendRemainder(int from, int to, Appendable destination) throws IOException {
            appendTo(from, to, destination);
            return to;
        }

        @Override
        public boolean needsTruncating() {
            return false;
        }

        @Override
        public void truncate() {
            throw new UnsupportedOperationException();
        }
    }

    final class OfReader implements Source, Appendable {

        static final String LOGGER_NAME = "com.github.robtimus.obfuscation.xml"; //$NON-NLS-1$

        static final String PREFERRED_MAX_BUFFER_SIZE_PROPERTY = "com.github.robtimus.obfuscation.xml.preferredMaxBufferSize"; //$NON-NLS-1$

        static final int DEFAULT_PREFERRED_MAX_BUFFER_SIZE = 512 * 1024; // 512KB / 0.5MB

        static final int PREFERRED_MAX_BUFFER_SIZE = getPreferredMaxBufferSize();

        private final CountingReader reader;

        private final StringBuilder buffer;

        private int offset;
        private int firstUnread;

        private final Logger logger;

        OfReader(CountingReader reader, Logger logger) {
            this.reader = reader;

            buffer = new StringBuilder();

            offset = 0;
            firstUnread = 0;

            this.logger = logger;
        }

        @Override
        public char charAt(int index) {
            return buffer.charAt(index - offset);
        }

        @Override
        public int length() {
            return buffer.length() + offset;
        }

        @Override
        public int skipLeadingWhitespace(int fromIndex, int toIndex) {
            return ObfuscatorUtils.skipLeadingWhitespace(buffer, fromIndex - offset, toIndex - offset) + offset;
        }

        @Override
        public int skipTrailingWhitespace(int fromIndex, int toIndex) {
            return ObfuscatorUtils.skipTrailingWhitespace(buffer, fromIndex - offset, toIndex - offset) + offset;
        }

        @Override
        public void appendTo(int from, int to, Appendable destination) throws IOException {
            destination.append(buffer, from - offset, to - offset);
            firstUnread = Math.max(firstUnread, to);

            // Truncate if necessary, now that firstUnread has been updated
            truncateIfNeeded(0);
        }

        @Override
        public void obfuscateText(int from, int to, Obfuscator obfuscator, Appendable destination) throws IOException {
            obfuscator.obfuscateText(buffer, from - offset, to - offset, destination);
            firstUnread = Math.max(firstUnread, to);

            // Truncate if necessary, now that firstUnread has been updated
            truncateIfNeeded(0);
        }

        @Override
        public int appendRemainder(int from, int to, Appendable destination) throws IOException {
            // First append from the buffer, as that's already been read from the Reader.
            // to will be -1 at this point, so ignore it
            destination.append(buffer, from - offset, buffer.length());
            firstUnread = buffer.length() + offset;

            // Now copy everything from the Reader without appending to the buffer
            copyAll(reader, destination);

            return (int) Math.min(Integer.MAX_VALUE, reader.count());
        }

        @Override
        public boolean needsTruncating() {
            return buffer.length() > PREFERRED_MAX_BUFFER_SIZE;
        }

        @Override
        public void truncate() {
            if (firstUnread == offset) {
                return;
            }

            if (logger.isTraceEnabled()) {
                logger.trace(Messages.Source.truncating(buffer.length()));
            }

            buffer.delete(0, firstUnread - offset);
            offset = firstUnread;

            if (logger.isTraceEnabled()) {
                logger.trace(Messages.Source.truncated(buffer.length()));
            }
        }

        @Override
        public Appendable append(char c) {
            truncateIfNeeded(1);
            buffer.append(c);
            return this;
        }

        @Override
        public Appendable append(CharSequence csq) {
            CharSequence cs = csq == null ? "null" : csq; //$NON-NLS-1$
            truncateIfNeeded(cs.length());
            buffer.append(cs);
            return this;
        }

        @Override
        public Appendable append(CharSequence csq, int start, int end) {
            CharSequence cs = csq == null ? "null" : csq; //$NON-NLS-1$
            truncateIfNeeded(end - start);
            buffer.append(cs, start, end);
            return this;
        }

        private void truncateIfNeeded(int additional) {
            if (buffer.length() + additional > PREFERRED_MAX_BUFFER_SIZE) {
                truncate();
            }
        }

        static int getPreferredMaxBufferSize() {
            final int defaultPreferredMaxBufferSize = DEFAULT_PREFERRED_MAX_BUFFER_SIZE;
            String preferredMaxBufferSizeString = System.getProperty(PREFERRED_MAX_BUFFER_SIZE_PROPERTY);
            if (preferredMaxBufferSizeString != null) {
                try {
                    int preferredMaxBufferSize = Integer.parseInt(preferredMaxBufferSizeString);
                    if (preferredMaxBufferSize > 0) {
                        return preferredMaxBufferSize;
                    }
                    String message = Messages.Source.preferredMaxBufferSize.notPositive(preferredMaxBufferSize, defaultPreferredMaxBufferSize);
                    LoggerFactory.getLogger(LOGGER_NAME).warn(message);

                } catch (@SuppressWarnings("unused") NumberFormatException e) {
                    String message = Messages.Source.preferredMaxBufferSize.notNumeric(preferredMaxBufferSizeString, defaultPreferredMaxBufferSize);
                    LoggerFactory.getLogger(LOGGER_NAME).warn(message);
                }
            }
            return defaultPreferredMaxBufferSize;
        }
    }
}
