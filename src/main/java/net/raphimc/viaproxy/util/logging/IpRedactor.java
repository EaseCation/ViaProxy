/*
 * This file is part of ViaProxy - https://github.com/RaphiMC/ViaProxy
 * Copyright (C) 2021-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.raphimc.viaproxy.util.logging;

import net.raphimc.viaproxy.ViaProxy;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
import org.apache.logging.log4j.core.pattern.PatternConverter;

@Plugin(name = "ip_redactor", category = PatternConverter.CATEGORY)
@ConverterKeys({"ip_redactor"})
public class IpRedactor extends LogEventPatternConverter {

    public static IpRedactor newInstance(final String[] options) {
        return new IpRedactor();
    }

    private IpRedactor() {
        super("IpRedactor", null);
    }

    @Override
    public void format(final LogEvent event, final StringBuilder toAppendTo) {
        if (ViaProxy.getConfig() != null && !ViaProxy.getConfig().shouldLogIps()) {
            final String message = IpAddressRedactor.redact(toAppendTo.toString());
            toAppendTo.setLength(0);
            toAppendTo.append(message);
        }
    }

}
