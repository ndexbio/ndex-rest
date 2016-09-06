/**
 * Copyright (c) 2013, 2016, The Regents of the University of California, The Cytoscape Consortium
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package org.ndexbio.common.models.object;

@Deprecated
public class MetaParameter
{
    private String _key;
    private char _operator;
    private String _keywords;
    
    
    
    /**************************************************************************
    * Default constructor - initializes the created date. 
    **************************************************************************/
    public MetaParameter()
    {
        
    }
    
    /**************************************************************************
    * Initializes the class and populates the properties. 
    **************************************************************************/
    public MetaParameter(String key, char operator, String keywords)
    {
        _key = key;
        _operator = operator;
        _keywords = keywords;
    }
    
    
    
    public String getKey()
    {
        return _key;
    }
    
    public void setKey(String key)
    {
        _key = key;
    }
    
    public char getOperator()
    {
        return _operator;
    }
    
    public void setOperator(char operator)
    {
        _operator = operator;
    }
    
    public String getKeywords()
    {
        return _keywords;
    }
    
    public void setKeywords(String keywords)
    {
        _keywords = keywords;
    }



    /**************************************************************************
    * Gets the SQL for the operator and keywords.
    *  
    * @return A string containing SQL for the operator.
    **************************************************************************/
    @Override
    public String toString()
    {
        switch (_operator)
        {
            case '=':
                return " = '" + _keywords + "'";
            case ':':
                return " LIKE '" + _keywords + "%'";
            case '~':
                return " LIKE '%" + _keywords + "%'";
            default:
                throw new IllegalArgumentException ("Unsupported operator encountered: " + _operator);
        }
    }
}
