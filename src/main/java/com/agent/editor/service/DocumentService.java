package com.agent.editor.service;

import com.agent.editor.dto.DiffResult;
import com.agent.editor.model.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DocumentService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);
    
    private final Map<String, Document> documents = new ConcurrentHashMap<>();
    private final Map<String, List<DiffResult>> diffHistory = new ConcurrentHashMap<>();

    public DocumentService() {
        Document sampleDoc = new Document(
        "doc-001",
        "Long Document",
        """
                # LangChain 入门指南
                
                ## 目录
                
                - [简介](#简介)
                - [核心概念](#核心概念)
                - [快速开始](#快速开始)
                - [组件详解](#组件详解)
                - [实战案例](#实战案例)
                - [最佳实践](#最佳实践)
                
                ---
                
                ## 简介
                
                ### 什么是 LangChain？
                
                LangChain 是一个用于开发由**大语言模型（LLM）驱动的应用程序**的框架。它提供了一套标准化的接口和工具，让开发者能够：
                
                1. **快速原型开发** - 在几分钟内构建 LLM 应用原型
                2. **组件化设计** - 模块化的组件可以自由组合
                3. **跨模型兼容** - 支持 OpenAI、Anthropic、本地模型等多种 LLM
                
                ### 为什么选择 LangChain？
                
                | 特性 | 描述 | 适用场景 |
                |------|------|----------|
                | 统一接口 | 同一套代码切换不同 LLM | 多模型对比测试 |
                | 记忆系统 | 内置对话记忆管理 | 聊天机器人 |
                | 工具调用 | LLM 可调用外部工具 | Agent 应用 |
                | 链式调用 | 多步骤任务编排 | 复杂工作流 |
                
                ---
                
                ## 核心概念
                
                LangChain 的核心由以下几个模块组成：
                
                ### 2.1 模型
                
                模型是 LangChain 的基础，分为两类：
                
                #### 2.1.1 LLM（大语言模型）
                
                传统的文本输入-文本输出模型：
                
                ```python
                from langchain_openai import OpenAI
                
                # 初始化 LLM
                llm = OpenAI(temperature=0.7)
                
                # 调用模型
                response = llm.invoke("什么是机器学习？")
                print(response)
                ```
                
                #### 2.1.2 Chat Models（聊天模型）
                
                支持多轮对话的模型，使用消息格式：
                
                ```python
                from langchain_openai import ChatOpenAI
                from langchain_core.messages import HumanMessage, SystemMessage
                
                # 初始化聊天模型
                chat = ChatOpenAI(model="gpt-4", temperature=0)
                
                # 构建消息
                messages = [
                    SystemMessage(content="你是一个专业的 Python 开发者"),
                    HumanMessage(content="如何优雅地处理异常？")
                ]
                
                # 调用模型
                response = chat.invoke(messages)
                print(response.content)
                ```
                
                > **提示**：温度参数（temperature）控制输出的随机性。`temperature=0` 最确定性，适合代码生成；`temperature=0.7` 平衡创造性和一致性；`temperature=1.0` 最大随机性，适合创意写作。
                
                ### 2.2 提示模板
                
                #### 基础模板
                
                ```python
                from langchain_core.prompts import PromptTemplate
                
                # 创建模板
                template = PromptTemplate.from_template(
                    "请用{style}的风格，解释{concept}这个概念"
                )
                
                # 渲染模板
                prompt = template.invoke({
                    "style": "通俗易懂",
                    "concept": "递归"
                })
                print(prompt.text)
                ```
                
                #### 聊天提示模板
                
                ```python
                from langchain_core.prompts import ChatPromptTemplate
                
                # 创建聊天模板
                chat_template = ChatPromptTemplate.from_messages([
                    ("system", "你是一个{role}，擅长{skill}"),
                    ("human", "{question}")
                ])
                
                # 渲染
                messages = chat_template.invoke({
                    "role": "资深架构师",
                    "skill": "系统设计",
                    "question": "如何设计一个高并发系统？"
                })
                ```
                
                ### 2.3 输出解析器
                
                将 LLM 的文本输出转换为结构化数据：
                
                ```python
                from langchain_core.output_parsers import JsonOutputParser
                from langchain_core.pydantic_v1 import BaseModel, Field
                
                # 定义数据模型
                class Person(BaseModel):
                    name: str = Field(description="姓名")
                    age: int = Field(description="年龄")
                    hobbies: list[str] = Field(description="爱好列表")
                
                # 创建解析器
                parser = JsonOutputParser(pydantic_object=Person)
                
                # 在提示中使用
                prompt = PromptTemplate.from_template(
                    "请提取以下文本中的人物信息：\\n{text}\\n\\n{format_instructions}"
                ).partial(format_instructions=parser.get_format_instructions())
                
                # 解析输出
                result = parser.parse(llm_output)
                ```
                
                ---
                
                ## 快速开始
                
                ### 环境准备
                
                **第一步：安装依赖**
                
                ```bash
                # 基础安装
                pip install langchain langchain-openai
                
                # 可选：向量数据库
                pip install langchain-chroma
                
                # 可选：文本分割
                pip install langchain-text-splitters
                ```
                
                **第二步：配置 API Key**
                
                ```bash
                # 方式一：环境变量
                export OPENAI_API_KEY="sk-your-api-key"
                
                # 方式二：.env 文件
                echo "OPENAI_API_KEY=sk-your-api-key" > .env
                ```
                
                ### 你的第一个 Chain
                
                ```python
                from langchain_openai import ChatOpenAI
                from langchain_core.prompts import ChatPromptTemplate
                from langchain_core.output_parsers import StrOutputParser
                
                # 1. 定义模型
                model = ChatOpenAI(model="gpt-4o-mini")
                
                # 2. 定义提示模板
                prompt = ChatPromptTemplate.from_template(
                    "给我讲一个关于{topic}的笑话"
                )
                
                # 3. 定义输出解析器
                parser = StrOutputParser()
                
                # 4. 构建链（使用 LCEL 语法）
                chain = prompt | model | parser
                
                # 5. 调用链
                result = chain.invoke({"topic": "程序员"})
                print(result)
                ```
                
                > **重要**：LangChain 使用 `|` 操作符连接组件，这种语法称为 **LCEL（LangChain Expression Language）**，它让代码更简洁、可读性更强。
                
                ---
                
                ## 组件详解
                
                ### 4.1 链
                
                #### LLM Chain
                
                最基础的链，将提示、模型、解析器串联：
                
                ```python
                from langchain.chains import LLMChain
                
                # 传统方式（已不推荐）
                chain = LLMChain(llm=model, prompt=prompt)
                
                # 推荐使用 LCEL
                chain = prompt | model | parser
                ```
                
                #### 顺序链
                
                多个链按顺序执行：
                
                ```python
                from langchain_core.runnables import RunnablePassthrough
                
                # 定义两个处理步骤
                explain_step = ChatPromptTemplate.from_template(
                    "用简单的话解释：{concept}"
                ) | model | parser
                
                example_step = ChatPromptTemplate.from_template(
                    "给'{explanation}'举一个实际例子"
                ) | model | parser
                
                # 串联
                sequential_chain = (
                    {"explanation": explain_step}
                    | example_step
                )
                ```
                
                ### 4.2 记忆
                
                #### 对话记忆
                
                ```python
                from langchain_core.chat_history import InMemoryChatMessageHistory
                from langchain_core.runnables import RunnableWithMessageHistory
                
                # 创建消息存储
                store = {}
                
                def get_session_history(session_id: str):
                    if session_id not in store:
                        store[session_id] = InMemoryChatMessageHistory()
                    return store[session_id]
                
                # 包装链
                chain_with_history = RunnableWithMessageHistory(
                    chain,
                    get_session_history,
                    input_messages_key="input",
                    history_messages_key="chat_history"
                )
                
                # 多轮对话
                chain_with_history.invoke(
                    {"input": "你好！"},
                    config={"configurable": {"session_id": "user-123"}}
                )
                ```
                
                ### 4.3 检索增强生成（RAG）
                
                RAG 是 LangChain 最强大的功能之一，工作流程如下：
                
                ```
                ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
                │   用户问题   │ ──▶ │  向量检索   │ ──▶ │   相关文档   │
                └─────────────┘     └─────────────┘     └─────────────┘
                                                              │
                                                              ▼
                ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
                │   最终答案   │ ◀── │   LLM生成   │ ◀── │  提示+上下文  │
                └─────────────┘     └─────────────┘     └─────────────┘
                ```
                
                #### 完整 RAG 示例
                
                ```python
                from langchain_community.document_loaders import TextLoader
                from langchain_text_splitters import RecursiveCharacterTextSplitter
                from langchain_openai import OpenAIEmbeddings
                from langchain_chroma import Chroma
                from langchain.chains import create_retrieval_chain
                from langchain.chains.combine_documents import create_stuff_documents_chain
                
                # 1. 加载文档
                loader = TextLoader("./knowledge.txt")
                docs = loader.load()
                
                # 2. 文本分割
                text_splitter = RecursiveCharacterTextSplitter(
                    chunk_size=1000,
                    chunk_overlap=200
                )
                splits = text_splitter.split_documents(docs)
                
                # 3. 创建向量存储
                vectorstore = Chroma.from_documents(
                    documents=splits,
                    embedding=OpenAIEmbeddings()
                )
                
                # 4. 创建检索器
                retriever = vectorstore.as_retriever(
                    search_type="similarity",
                    search_kwargs={"k": 3}
                )
                
                # 5. 构建 RAG 链
                system_prompt = ""\"使用以下上下文回答问题：
                {context}
                ""\"
                
                prompt = ChatPromptTemplate.from_messages([
                    ("system", system_prompt),
                    ("human", "{input}")
                ])
                
                question_answer_chain = create_stuff_documents_chain(model, prompt)
                rag_chain = create_retrieval_chain(retriever, question_answer_chain)
                
                # 6. 查询
                response = rag_chain.invoke({"input": "文档的主要观点是什么？"})
                print(response["answer"])
                ```
                
                ### 4.4 智能体
                
                让 LLM 自主决定使用哪些工具：
                
                ```python
                from langchain_community.tools import DuckDuckGoSearchRun
                from langchain.agents import create_tool_calling_agent, AgentExecutor
                
                # 定义工具
                tools = [
                    DuckDuckGoSearchRun(name="search")
                ]
                
                # 创建智能体
                prompt = ChatPromptTemplate.from_messages([
                    ("system", "你是一个有用的助手，可以使用工具获取信息。"),
                    ("human", "{input}"),
                    ("placeholder", "{agent_scratchpad}")
                ])
                
                agent = create_tool_calling_agent(model, tools, prompt)
                agent_executor = AgentExecutor(agent=agent, tools=tools, verbose=True)
                
                # 执行
                result = agent_executor.invoke({
                    "input": "今天北京的天气怎么样？"
                })
                ```
                
                ---
                
                ## 实战案例
                
                ### 案例1：文档问答助手
                
                构建一个能够回答 PDF 文档问题的助手：
                
                ```python
                from langchain_community.document_loaders import PyPDFLoader
                from langchain_openai import OpenAIEmbeddings
                from langchain_chroma import Chroma
                from langchain_text_splitters import RecursiveCharacterTextSplitter
                
                def build_pdf_qa(pdf_path: str):
                    ""\"构建 PDF 问答系统""\"
                
                    # 加载 PDF
                    loader = PyPDFLoader(pdf_path)
                    pages = loader.load_and_split()
                
                    # 分割文本
                    splitter = RecursiveCharacterTextSplitter(
                        chunk_size=500,
                        chunk_overlap=50
                    )
                    chunks = splitter.split_documents(pages)
                
                    # 创建向量库
                    db = Chroma.from_documents(
                        chunks,
                        OpenAIEmbeddings()
                    )
                
                    return db.as_retriever()
                
                # 使用
                retriever = build_pdf_qa("./manual.pdf")
                docs = retriever.invoke("如何安装？")
                ```
                
                ### 案例2：多轮对话机器人
                
                ```python
                from langchain_openai import ChatOpenAI
                from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
                from langchain_core.runnables import RunnablePassthrough
                from langchain_core.chat_history import BaseChatMessageHistory
                from langchain_community.chat_message_histories import ChatMessageHistory
                from langchain_core.runnables import RunnableWithMessageHistory
                
                # 初始化
                model = ChatOpenAI(model="gpt-4o-mini")
                store = {}
                
                def get_history(session_id: str) -> BaseChatMessageHistory:
                    if session_id not in store:
                        store[session_id] = ChatMessageHistory()
                    return store[session_id]
                
                # 构建对话链
                prompt = ChatPromptTemplate.from_messages([
                    ("system", "你是一个友好、专业的客服助手。"),
                    MessagesPlaceholder(variable_name="history"),
                    ("human", "{question}")
                ])
                
                chain = prompt | model
                
                # 添加记忆
                chain_with_history = RunnableWithMessageHistory(
                    chain,
                    get_history,
                    input_messages_key="question",
                    history_messages_key="history"
                )
                
                # 对话
                def chat(question: str, session_id: str = "default"):
                    return chain_with_history.invoke(
                        {"question": question},
                        config={"configurable": {"session_id": session_id}}
                    )
                ```
                
                ### 案例3：结构化输出
                
                ```python
                from langchain_core.pydantic_v1 import BaseModel, Field
                from typing import List
                
                # 定义输出结构
                class ProductReview(BaseModel):
                    product_name: str = Field(description="产品名称")
                    rating: int = Field(ge=1, le=5, description="评分1-5")
                    pros: List[str] = Field(description="优点列表")
                    cons: List[str] = Field(description="缺点列表")
                    summary: str = Field(description="一句话总结")
                
                # 使用 with_structured_output
                model = ChatOpenAI(model="gpt-4o-mini")
                structured_model = model.with_structured_output(ProductReview)
                
                result = structured_model.invoke(""\"
                我刚买了 iPhone 15 Pro，用了一周很满意。优点是钛合金边框手感好、
                相机夜景给力、性能流畅。缺点是充电速度一般、价格偏高。
                总体来说是款优秀的旗舰机，给4分。
                ""\")
                
                print(result)
                # ProductReview(
                #     product_name="iPhone 15 Pro",
                #     rating=4,
                #     pros=["钛合金边框手感好", "相机夜景给力", "性能流畅"],
                #     cons=["充电速度一般", "价格偏高"],
                #     summary="优秀的旗舰机，但价格偏高"
                # )
                ```
                
                ---
                
                ## 最佳实践
                
                ### 提示工程技巧
                
                1. **明确角色定位**
                
                   ```
                   你是一个{专业领域}专家，擅长{具体技能}...
                   ```
                
                2. **提供示例（Few-shot）**
                
                   ```python
                   prompt = ChatPromptTemplate.from_messages([
                       ("system", "将句子翻译成{language}："),
                       ("human", "Hello"),
                       ("ai", "你好"),
                       ("human", "Good morning"),
                       ("ai", "早上好"),
                       ("human", "{input}")
                   ])
                   ```
                
                3. **结构化输出指令**
                
                   ```
                   请按以下格式输出：
                   1. 标题
                   2. 摘要
                   3. 要点（至少3个）
                   ```
                
                ### 性能优化
                
                | 问题 | 解决方案 |
                |------|----------|
                | 响应慢 | 使用 `gpt-4o-mini` 替代 `gpt-4` |
                | Token 消耗大 | 减少上下文长度，使用 `chunk_overlap` 优化 |
                | 成本高 | 实现本地缓存，避免重复调用 |
                
                ### 常见陷阱
                
                - [x] 不要在提示中硬编码敏感信息
                - [x] 总是限制最大 token 数
                - [x] 实现超时和重试机制
                - [ ] 使用全局变量存储状态（应使用会话隔离）
                
                > **警告**：
                > 1. **API Key 安全**：永远不要在代码中硬编码 API Key
                > 2. **成本控制**：设置合理的 `max_tokens` 限制
                > 3. **错误处理**：所有外部调用都要有 try-catch
                
                ---
                
                ## 参考资源
                
                ### 官方文档
                
                - [LangChain 官方文档](https://python.langchain.com/)
                - [LangChain GitHub](https://github.com/langchain-ai/langchain)
                - [LangSmith 追踪平台](https://www.langchain.com/langsmith)
                
                ### 推荐阅读
                
                1. LLM 应用开发实践
                2. 向量数据库选型指南
                3. Prompt Engineering 完全指南
                
                ### 术语表
                
                | 术语 | 英文 | 解释 |
                |------|------|------|
                | 链 | Chain | 多个组件串联的处理流程 |
                | 提示 | Prompt | 输入给 LLM 的文本模板 |
                | 嵌入 | Embedding | 将文本转换为向量表示 |
                | 检索器 | Retriever | 从向量库查询相关文档的组件 |
                | 智能体 | Agent | 能够自主决策和调用工具的 LLM |
                
                ---
                
                ## 更新日志
                
                - **2026-04-03**：创建文档
                - **待更新**：添加 LangGraph 章节
                
                ---
                
                > "The best way to learn LangChain is to build something with it."
                >\s
                > —— Harrison Chase, LangChain Creator
                
                *Happy Coding! 🚀*
                """
        );
        Document sampleDoc2 = new Document(
                "doc-002",
                "Short Document",
                """
                        多年以后，面对行刑队，奥雷里亚诺·布恩迪亚上校将会回想起父亲带他去见识冰块的那个遥远的下午。
                        
                        当时，马孔多是个二十户人家的村庄，一座座土房都盖在河岸上，河水清澈，沿着遍布石头的河床流去，河里的石头光滑、洁白，活像史前的巨蛋。
                        
                        这块天地还是新开辟的，许多东西都叫不出名字，不得不用手指指点点。
                        
                        每年三月，衣衫褴褛的吉卜赛人都会在村边搭起帐篷，在笛鼓的喧嚣声中，向马孔多的居民介绍科学家的最新发明。
                        """);
        documents.put(sampleDoc.getId(), sampleDoc);
        documents.put(sampleDoc2.getId(), sampleDoc2);
    }

    public Document createDocument(String title, String content) {
        String id = "doc-" + UUID.randomUUID().toString().substring(0, 8);
        Document doc = new Document(id, title, content);
        documents.put(id, doc);
        logger.info("Document created: {}", id);
        return doc;
    }

    public Document getDocument(String id) {
        return documents.get(id);
    }

    public List<Document> getAllDocuments() {
        return new ArrayList<>(documents.values());
    }

    public Document updateDocument(String id, String content) {
        Document doc = documents.get(id);
        if (doc != null) {
            doc.setContent(content);
            doc.setUpdatedAt(LocalDateTime.now());
            logger.info("Document updated: {}", id);
        }
        return doc;
    }

    public boolean deleteDocument(String id) {
        logger.info("Document deleted: {}", id);
        return documents.remove(id) != null;
    }

    public DiffResult generateDiff(String documentId, String originalContent, String modifiedContent) {
        String diffHtml = computeSimpleDiff(originalContent, modifiedContent);
        return new DiffResult(originalContent, modifiedContent, diffHtml);
    }

    public DiffResult recordDiff(String documentId, String originalContent, String modifiedContent) {
        DiffResult diff = generateDiff(documentId, originalContent, modifiedContent);
        diffHistory.computeIfAbsent(documentId, ignored -> new ArrayList<>()).add(diff);
        return diff;
    }

    public List<DiffResult> getDiffHistory(String documentId) {
        return diffHistory.getOrDefault(documentId, Collections.emptyList());
    }

    private String computeSimpleDiff(String original, String modified) {
        if (original == null) original = "";
        if (modified == null) modified = "";
        
        String[] originalLines = original.split("\n");
        String[] modifiedLines = modified.split("\n");
        
        StringBuilder diff = new StringBuilder();
        
        int i = 0, j = 0;
        while (i < originalLines.length || j < modifiedLines.length) {
            if (i >= originalLines.length) {
                diff.append("<div class='diff-add'>+ ").append(escapeHtml(modifiedLines[j])).append("</div>");
                j++;
            } else if (j >= modifiedLines.length) {
                diff.append("<div class='diff-remove'>- ").append(escapeHtml(originalLines[i])).append("</div>");
                i++;
            } else if (originalLines[i].equals(modifiedLines[j])) {
                diff.append("<div class='diff-same'>  ").append(escapeHtml(originalLines[i])).append("</div>");
                i++;
                j++;
            } else {
                diff.append("<div class='diff-remove'>- ").append(escapeHtml(originalLines[i])).append("</div>");
                diff.append("<div class='diff-add'>+ ").append(escapeHtml(modifiedLines[j])).append("</div>");
                i++;
                j++;
            }
        }
        
        return diff.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
