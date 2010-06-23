/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor.generator;

import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.xml.SAXStore;
import org.xml.sax.SAXException;

public class SAXStoreGenerator extends org.orbeon.oxf.processor.ProcessorImpl {

    private SAXStore saxStore;
    private OutputCacheKey key;
    private Object validity;

    public SAXStoreGenerator(SAXStore saxStore) {
        this.saxStore = saxStore;
    }

    public SAXStoreGenerator(SAXStore saxStore, OutputCacheKey key, Object validity) {
        this(saxStore);
        this.key = key;
        this.validity = validity;
    }

    public org.orbeon.oxf.processor.ProcessorOutput createOutput(String name) {
        org.orbeon.oxf.processor.ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {
                try {
                    if (saxStore != null) {
                        saxStore.replay(xmlReceiver);
                    } else {
                        throw new OXFException("SAXStore is not set on SAXStoreGenerator");
                    }
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }

            public OutputCacheKey getKeyImpl(org.orbeon.oxf.pipeline.api.PipelineContext context) {
                return key;
            }

            public Object getValidityImpl(org.orbeon.oxf.pipeline.api.PipelineContext context) {
                return validity;
            }
        };
        addOutput(name, output);
        return output;
    }
}
