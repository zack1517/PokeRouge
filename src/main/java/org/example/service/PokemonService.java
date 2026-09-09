package org.example.service;

import org.example.model.Species;

import java.util.List;

/**
 * 宝可梦系统对外服务接口。
 * <p>契约：接口文档 v1.0 §4.1（待组长评审）。当前仅文档列举的 {@link #getInitialPool()}，
 * 其余能力（个体创建/属性查询）由 {@code GameData} 承载；待接口文档补全后扩展。</p>
 */
public interface PokemonService {

    /** 初始池（I-01）：可选的初始宝可梦种族。 */
    List<Species> getInitialPool();
}
