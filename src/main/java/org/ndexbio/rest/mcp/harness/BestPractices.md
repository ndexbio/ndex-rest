## MCP Meta Descriptions 
First-class concern for every MCP tool is to remember that the tool must be self-describing and should leverage the MCP protocol mechanism of meta descriptions of the tool itself, input/output parameters and error/success response models to their fullest. 

Take it as opportunity to provide as much salient and semantic description of the tool and its behavior as possible so that llm's can reason proprely to compose the tool into dynamic sequences of interacting with cytoscape desktop mcp tools as a whole of building blocks to complete larger unstructured flows that a user asks.  

### Tool description, two sections are encouraged:  
* First section - 3-7 sentences describing functionality the tool provides, if more than is ok, but that is generally a good size. Include one additional sentence to mention error response handling and how the tool may emit error responses instead of successful responses and highlight that the error response should proivde clear indicator of reason and highlight any specific error message or types of errors if they are prominently known in the tool's implementation. 
* Second section - Examples. Separated by two line breaks after description. Provide at least 3 to 4 agent prompt examples that shold trigger activation by an LLM of this tool. Each example shows the prompt snippet and the example input params if applicable that LLM woud have populated as well when requesting invocation. Refer to other tools in project for examples.

### Tool input parameter descriptions
In addition to desribing each input parameter meaning and intent, must highlight whether the parameter is optional or required. State if presence of other parameters on the tool determine the optional/required aspect, thereby capturing the parameter's semantic relationship in the tool. Often, tools will have groups of parameters with some optional and some required, so clearly stating this aspect helps the LLM know how invoke the tool. Include 2-3 examlple values as additional last sentence in the parameter description, separated by two line breaks. Use McpSchema.InputSchema to define all input descriptions and serialize it to the json schema.

### Output schema descriptions
Tools should define a structured content model for tool output, not just string. The structured content should be Json as it works over the mcp protocol. Use Java record with jackson annoations which provide abitity to add schema level descriptions to all nodes in the content model. Take full advantage of this json schema and add a description for every element, attribute, sub-elelment of the response model and then set the tool output schema to that of the json schema definition. Include 2-3 examlple values as additional last sentence in the shcema description when the element has scalar value types, separated by two line breaks.

### Meta descriptions summary
The goal for each tool's meta descriptions is to be encapsulated unto itself and the descriptions should never reference any other tool by name in any of the meta descriptions. Rather it's encouraged to reference general functionality that another tool may represent in meta descriptions for context as that creates a semenatic web across tools but not by direct tool name, this allows the LLM to reason and activate tools when they are functionally needed.

Research Summary: Consensus on Tool Meta Best Practices
Sources: https://modelcontextprotocol.io/docs/concepts/tools, https://arxiv.org/html/2602.14878v1, https://github.com/modelcontextprotocol/modelcontextprotocol/issues/1382, https://modelcontextprotocol.info/docs/tutorials/writing-effective-tools/, https://www.arsturn.com/blog/maximizing-your-mcp-experience-tips-for-effective-tool-descriptions


### Guidelines

| # | Guideline | Applies to | Source |
|---|-----------|------------|--------|
| G1 | Lead with an imperative verb — one concise sentence for what the tool does | Tool description | MCP Spec, SEP-1382 |
| G2 | High-level purpose only in tool description — avoid parameter names, return-schema fields, implementation internals | Tool description | SEP-1382 |
| G3 | Include activation criteria — "when to use" hint for tool selection | Tool description | arxiv, Arsturn |
| G4 | State key side-effects — read-only vs. mutating, what gets created/changed | Tool description | arxiv |
| G5 | 2–3 sentences for tool description | Tool description | arxiv, SEP-1382 |
| G6 | Consistent voice — imperative-declarative across all tools | Tool description | Arsturn |
| G7 | No parameter names in tool description — those belong in schema descriptions | Tool description | SEP-1382 |
| P1 | Every output field needs a `@JsonPropertyDescription` — LLMs use these to interpret results and reason about next steps | Output params | SEP-1382, Writing Effective Tools |
| P2 | Conditional dependencies must be explicit — "Required when X. Ignored when Y." pattern helps LLMs reason about which params to populate | Input params | Writing Effective Tools |
| P3 | Consistent Required/Optional prefix — start each param description with "Required." or "Optional." for quick LLM scanning | Input params | Arsturn |
| P4 | Avoid prompt-engineering in schema descriptions — descriptions like "this is the most important parameter" are subjective directives, not documentation | Input params | SEP-1382 |
| P5 | Fix typos — LLMs may misinterpret misspelled terms | All | General |
| P6 | Conditionally-required params use "Optional." prefix — JSON Schema `required` only supports unconditional requirements. Params required only under certain conditions must NOT be in `.required()`. Instead, use description prefix "Optional." followed by the conditional: "Required when source='tabular-file'." The `.required()` list and descriptions must agree. | Input params | JSON Schema spec + MCP convention |
| P7 | Description prefix must match `.required()` list — if a param is in `.required()`, its description must start with "Required." If not in `.required()`, it must start with "Optional." even if conditionally required. | Input params | Consistency audit |

