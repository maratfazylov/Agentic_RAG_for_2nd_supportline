package com.hybridrag.tool;

import com.hybridrag.model.QueryContext;

public interface Tool {

    String getName();

    String getDescription();

    String execute(String args, QueryContext ctx);
}
