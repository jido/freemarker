/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
 
package org.apache.freemarker.dom;

import java.util.List;

import javax.xml.transform.TransformerException;

import org.apache.freemarker.core.Environment;
import org.apache.freemarker.core.Template;
import org.apache.freemarker.core.TemplateException;
import org.apache.freemarker.core.model.TemplateBooleanModel;
import org.apache.freemarker.core.model.TemplateModel;
import org.apache.freemarker.core.model.impl.SimpleNumber;
import org.apache.freemarker.core.model.impl.SimpleScalar;
import org.w3c.dom.Node;
import org.w3c.dom.traversal.NodeIterator;

import com.sun.org.apache.xml.internal.utils.PrefixResolver;
import com.sun.org.apache.xpath.internal.XPath;
import com.sun.org.apache.xpath.internal.XPathContext;
import com.sun.org.apache.xpath.internal.objects.XBoolean;
import com.sun.org.apache.xpath.internal.objects.XNodeSet;
import com.sun.org.apache.xpath.internal.objects.XNull;
import com.sun.org.apache.xpath.internal.objects.XNumber;
import com.sun.org.apache.xpath.internal.objects.XObject;
import com.sun.org.apache.xpath.internal.objects.XString;

/**
 * This is just the XalanXPathSupport class using the sun internal
 * package names
 */

class SunInternalXalanXPathSupport implements XPathSupport {
    
    private XPathContext xpathContext = new XPathContext();
        
    private static final String ERRMSG_RECOMMEND_JAXEN
            = "(Note that there is no such restriction if you "
                    + "configure FreeMarker to use Jaxen instead of Xalan.)";

    private static final String ERRMSG_EMPTY_NODE_SET
            = "Cannot perform an XPath query against an empty node set." + ERRMSG_RECOMMEND_JAXEN;
    
    @Override
    synchronized public TemplateModel executeQuery(Object context, String xpathQuery) throws TemplateException {
        if (!(context instanceof Node)) {
            if (context != null) {
                if (isNodeList(context)) {
                    int cnt = ((List) context).size();
                    if (cnt != 0) {
                        throw new TemplateException(
                                "Cannot perform an XPath query against a node set of " + cnt
                                + " nodes. Expecting a single node." + ERRMSG_RECOMMEND_JAXEN);
                    } else {
                        throw new TemplateException(ERRMSG_EMPTY_NODE_SET);
                    }
                } else {
                    throw new TemplateException(
                            "Cannot perform an XPath query against a " + context.getClass().getName()
                            + ". Expecting a single org.w3c.dom.Node.");
                }
            } else {
                throw new TemplateException(ERRMSG_EMPTY_NODE_SET);
            }
        }
        Node node = (Node) context;
        try {
            XPath xpath = new XPath(xpathQuery, null, customPrefixResolver, XPath.SELECT, null);
            int ctxtNode = xpathContext.getDTMHandleFromNode(node);
            XObject xresult = xpath.execute(xpathContext, ctxtNode, customPrefixResolver);
            if (xresult instanceof XNodeSet) {
                NodeListModel result = new NodeListModel(node);
                result.xpathSupport = this;
                NodeIterator nodeIterator = xresult.nodeset();
                Node n;
                do {
                    n = nodeIterator.nextNode();
                    if (n != null) {
                        result.add(n);
                    }
                } while (n != null);
                return result.size() == 1 ? result.get(0) : result;
            }
            if (xresult instanceof XBoolean) {
                return ((XBoolean) xresult).bool() ? TemplateBooleanModel.TRUE : TemplateBooleanModel.FALSE;
            }
            if (xresult instanceof XNull) {
                return null;
            }
            if (xresult instanceof XString) {
                return new SimpleScalar(xresult.toString());
            }
            if (xresult instanceof XNumber) {
                return new SimpleNumber(Double.valueOf(((XNumber) xresult).num()));
            }
            throw new TemplateException("Cannot deal with type: " + xresult.getClass().getName());
        } catch (TransformerException te) {
            throw new TemplateException(te);
        }
    }
    
    private static PrefixResolver customPrefixResolver = new PrefixResolver() {
        
        @Override
        public String getNamespaceForPrefix(String prefix, Node node) {
            return getNamespaceForPrefix(prefix);
        }
        
        @Override
        public String getNamespaceForPrefix(String prefix) {
            if (prefix.equals(Template.DEFAULT_NAMESPACE_PREFIX)) {
                return Environment.getCurrentEnvironment().getDefaultNS();
            }
            return Environment.getCurrentEnvironment().getNamespaceForPrefix(prefix);
        }
        
        @Override
        public String getBaseIdentifier() {
            return null;
        }
        
        @Override
        public boolean handlesNullPrefixes() {
            return false;
        }
    };
    
    /**
     * Used for generating more intelligent error messages.
     */
    private static boolean isNodeList(Object context) {
        if (context instanceof List) {
            List ls = (List) context;
            int ln = ls.size();
            for (int i = 0; i < ln; i++) {
                if (!(ls.get(i) instanceof Node)) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }
}