package com.fiap.techchallenge.os.usecase;

import com.fiap.techchallenge.cliente.domain.Cliente;
import com.fiap.techchallenge.cliente.usecase.ClienteGateway;
import com.fiap.techchallenge.os.adapter.controller.dto.TransicaoDeStatusDTO;
import com.fiap.techchallenge.exception.BadRequestException;
import com.fiap.techchallenge.os.domain.OrdemDeServico;
import com.fiap.techchallenge.os.domain.StatusOrdemDeServico;
import com.fiap.techchallenge.os.inputdata.AdicionarPecaItemInputData;
import com.fiap.techchallenge.peca.domain.Peca;
import com.fiap.techchallenge.peca.domain.TipoPeca;
import com.fiap.techchallenge.peca.usecase.TipoPecaGateway;
import com.fiap.techchallenge.servico.domain.Servico;
import com.fiap.techchallenge.servico.domain.ServicoStatus;
import com.fiap.techchallenge.servico.domain.TipoServico;
import com.fiap.techchallenge.servico.usecase.ServicoUseCase;
import com.fiap.techchallenge.servico.usecase.gateway.TipoServicoGateway;
import com.fiap.techchallenge.veiculo.domain.Veiculo;
import com.fiap.techchallenge.veiculo.usecase.VeiculoGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrdemDeServicoInteractor implements OrdemDeServicoUseCase {

    private static final String OS_NAO_EXISTE_MSG_TEMPLATE = "Erro: Ordem de Serviço '%d' não existe!";

    private final OrdemDeServicoGateway osGateway;
    private final ClienteGateway clienteGateway;
    private final VeiculoGateway veiculoGateway;
    private final TipoServicoGateway tipoServicoGateway;
    private final TipoPecaGateway tipoPecaGateway;
    private final ServicoUseCase servicoUseCase;

    @Autowired
    public OrdemDeServicoInteractor(
            OrdemDeServicoGateway osGateway,
            TipoPecaGateway tipoPecaGateway,
            ClienteGateway clienteGateway, VeiculoGateway veiculoGateway,
            TipoServicoGateway tipoServicoGateway, ServicoUseCase servicoUseCase) {
        this.osGateway = osGateway;
        this.clienteGateway = clienteGateway;
        this.veiculoGateway = veiculoGateway;
        this.tipoPecaGateway = tipoPecaGateway;
        this.tipoServicoGateway = tipoServicoGateway;
        this.servicoUseCase = servicoUseCase;
    }

    // ###########################################################################################
    // # CRUD
    // ###########################################################################################

    @Override
    public List<OrdemDeServico> getAllOrdemDeServico() {
        return this.osGateway.findAllOrdensDeServico();
    }

    @Override
    public Optional<OrdemDeServico> getOrdemDeServico(Long id) {
        return this.osGateway.findOrdemDeServicoById(id);
    }

    @Override
    public List<OrdemDeServico> getOrdensDeServicoPorClienteId(Long clienteId) {
        return this.osGateway.findAllOrdensDeServicoClienteId(clienteId);
    }

    @Override
    public List<OrdemDeServico> getOrdensDeServicoPorVeiculoId(Long veiculoId) {
        return this.osGateway.findAllOrdensDeServicoVeiculoId(veiculoId);
    }

    @Transactional
    @Override
    public OrdemDeServico createOrdemDeServico(String solicitacao, Long idCliente, Long idVeiculo, List<AdicionarPecaItemInputData> adicionarPecaItemInputData, List<Long> adicionarServicos) {
        Optional<Cliente> cOpt = this.clienteGateway.findById(idCliente);
        Optional<Veiculo> vOpt = this.veiculoGateway.findById(idVeiculo);

        if (cOpt.isEmpty()) {
            String message = "Erro: Cliente '%d' não existe!".formatted(idCliente);
            log.error(message);
            throw new BadRequestException(message);
        }

        if (vOpt.isEmpty()) {
            String message = "Erro: Veículo '%d' não existe!".formatted(idVeiculo);
            log.error(message);
            throw new BadRequestException(message);
        }

        OrdemDeServico novaOs = new OrdemDeServico();
        novaOs.setSolicitacao(solicitacao);
        novaOs.setOrcamento(BigDecimal.valueOf(0L, 2));
        novaOs.setDataHoraCriacao(LocalDateTime.now());
        novaOs.setCliente(cOpt.get());
        novaOs.setVeiculo(vOpt.get());
        novaOs.setStatus(StatusOrdemDeServico.RECEBIDA);

        novaOs = this.osGateway.saveOrdemDeServico(novaOs);

        if (adicionarPecaItemInputData != null && !adicionarPecaItemInputData.isEmpty()) {
            novaOs = adicionarPecasNaOrdemDeServico(novaOs, adicionarPecaItemInputData);
        }

        if (adicionarServicos != null && !adicionarServicos.isEmpty()) {
            novaOs = adicionarServicosNaOrdemDeServico(novaOs, adicionarServicos);
        }

        log.info("Ordem de Servico {} criada com sucesso!", novaOs.getId());

        return novaOs;
    }

    @Override
    public OrdemDeServico updateOrdemDeServico(Long id, BigDecimal orcamento, String status) {
        Optional<OrdemDeServico> osAtualOpt = this.osGateway.findOrdemDeServicoById(id);

        if (osAtualOpt.isPresent()) {
            var osEditada = osAtualOpt.get();
            if (orcamento != null) osEditada.setOrcamento(orcamento);
            if (status != null) {
                StatusOrdemDeServico statusEnum = StatusOrdemDeServico.valueOf(status);
                if (osEditada.podeTransicionarParaStatus(statusEnum).permitida()) {
                    osEditada.setStatus(statusEnum);
                } else {
                    throw new BadRequestException("Erro! A alteração de status é inválida!");
                }
            }

            osEditada = this.osGateway.updateOrdemDeServico(osEditada);

            return osEditada;
        } else {
            String message = getMensagemOsNaoExiste(id);
            log.error(message);
            throw new BadRequestException(message);
        }
    }

    @Override
    public OrdemDeServico deleteOrdemDeServico(Long id) {
        Optional<OrdemDeServico> osAtualOpt = this.osGateway.findOrdemDeServicoById(id);

        if (osAtualOpt.isPresent()) {
            var osDeletada = osAtualOpt.get();
            this.osGateway.deleteOrdemDeServico(osDeletada);
            return osDeletada;
        } else {
            String message = getMensagemOsNaoExiste(id);
            log.error(message);
            throw new BadRequestException(message);
        }
    }

    // ###########################################################################################
    // # Comandos
    // ###########################################################################################

    @Override
    @Transactional
    public TransicaoDeStatusDTO mudarParaStatus(Long id, String novoStatus) {
        OrdemDeServico os = this.osGateway.findOrdemDeServicoById(id).orElse(null);
        StatusOrdemDeServico novoStatusEnum = StatusOrdemDeServico.valueOf(novoStatus);

        if (os != null) {
            OrdemDeServico.ValidacaoTransicaoDeStatus validacao = os.podeTransicionarParaStatus(novoStatusEnum);
            if (validacao.permitida()) {
                os.setStatus(novoStatusEnum);
                this.osGateway.updateOrdemDeServico(os);
                return executarOperacoesParaTransicao(os, novoStatusEnum);
            } else if (os.getStatus().equals(novoStatusEnum)) {
                String erro = "Erro: A Ordem de Serviço %d já está no status '%s'!".formatted(os.getId(), novoStatusEnum);
                log.error(erro);
                return new TransicaoDeStatusDTO(false, erro);
            } else {
                log.error(validacao.erroDeValidacao());
                return new TransicaoDeStatusDTO(false, validacao.erroDeValidacao());
            }
        }
        return new TransicaoDeStatusDTO(false, getMensagemOsNaoExiste(id));
    }

    private TransicaoDeStatusDTO executarOperacoesParaTransicao(OrdemDeServico os, StatusOrdemDeServico novoStatus) {
        var mensagem = "A Ordem de Serviço %d mudou para '%s'".formatted(os.getId(), novoStatus);
        var dto = TransicaoDeStatusDTO.builder().sucesso(true).build();

        switch (os.getStatus()) {
            case AGUARDANDO_APROVACAO -> dto.setMensagem(enviarEmailDeSoliciatacaoDeAprovacao(os));
            case APROVADA -> dto.setMensagem(receberEmailComAprovacao(os));
            case EM_EXECUCAO -> dto.setMensagem(iniciarServicos(os));
            case FINALIZADA -> dto.setMensagem(finalizarServicos(os));
            default -> dto.setMensagem(mensagem);
        };

        return dto;
    }

    private String enviarEmailDeSoliciatacaoDeAprovacao(OrdemDeServico os) {
        return "Email de aprovação foi enviado para o cliente %s no endereço %s.".formatted(os.getCliente().getNome(), os.getCliente().getEmail());
    }

    private String receberEmailComAprovacao(OrdemDeServico os) {
        return "Email com a aprovação do cliente %s recebido!".formatted(os.getCliente().getNome());
    }

    private String receberEmailComReprovacao(OrdemDeServico os) {
        return "Email com a recusa de aprovação do cliente %s recebido!".formatted(os.getCliente().getNome());
    }

    private String iniciarServicos(OrdemDeServico os) {
        os.getServicos().forEach(servico -> this.servicoUseCase.iniciarServico(servico.getId()));
        return "Os servicos foram iniciados!";
    }

    private String finalizarServicos(OrdemDeServico os) {
        os.getServicos().forEach(servico -> {
            if (ServicoStatus.EM_EXECUCAO.equals(servico.getStatus())) this.servicoUseCase.finalizarServico(servico.getId());
        });
        return "Ordem de Serviço finalizada.";
    }

    private boolean alteracaoDeServicosOuPecasEPermitida(StatusOrdemDeServico osStatus) {
        if (!StatusOrdemDeServico.RECEBIDA.equals(osStatus) && !StatusOrdemDeServico.EM_DIAGNOSTICO.equals(osStatus) && !StatusOrdemDeServico.AGUARDANDO_APROVACAO.equals(osStatus))
            throw new BadRequestException("Alteração de Serviços e Peças só é permitida nos status '%s', '%s' e '%s', porém Ordem de Serviço se encontra no status '%s'!".formatted(StatusOrdemDeServico.RECEBIDA.name(), StatusOrdemDeServico.EM_DIAGNOSTICO.name(), StatusOrdemDeServico.AGUARDANDO_APROVACAO.name(), osStatus.name()));
        return true;
    }

    @Transactional
    @Override
    public OrdemDeServico putServicosNaOrdemDeServico(Long id, List<Long> idTipoServicosParaAdicionar) {
        if (idTipoServicosParaAdicionar != null && !idTipoServicosParaAdicionar.isEmpty()) {
            Optional<OrdemDeServico> osOpt = this.osGateway.findOrdemDeServicoById(id);

            if (osOpt.isPresent()) {
                return adicionarServicosNaOrdemDeServico(osOpt.get(), idTipoServicosParaAdicionar);
            }
        }

        return null;
    }

    @Transactional
    protected OrdemDeServico adicionarServicosNaOrdemDeServico(OrdemDeServico os, List<Long> idTipoServicosParaAdicionar) {
        if (os != null && alteracaoDeServicosOuPecasEPermitida(os.getStatus()) && idTipoServicosParaAdicionar != null && !idTipoServicosParaAdicionar.isEmpty()) {
            List<Servico> servicosDaOs = os.getServicos();

            List<Servico> servicosParaAdd = criarServicoParaAdicionarNaOrdemDeServico(os, idTipoServicosParaAdicionar);

            if (!servicosParaAdd.isEmpty()) {
                // Inicializa lista de serviços da entidade se necessário
                if (servicosDaOs == null) {
                    servicosDaOs = new ArrayList<>();
                    os.setServicos(servicosDaOs);
                }

                List<Servico> servicosFinal = servicosDaOs;

                servicosParaAdd.forEach(servicoAdd -> {
                    var idTipoServicoParaAdd = servicoAdd.getTipoServico().getId();

                    // Verifica se serviço já existe na Ordem de Serviço
                    var tipoServicoJaExiste = servicosFinal.stream()
                            .filter(sf -> sf.getTipoServico().getId().equals(idTipoServicoParaAdd))
                            .findFirst();

                    // Caso serviço já exista ele é ignorado
                    if (tipoServicoJaExiste.isEmpty()) {
                        servicosFinal.add(servicoAdd);
                    }
                });
                os.calcularOrcamento();
                os = this.osGateway.updateOrdemDeServico(os);

                log.info("Serviços adicionadas à OS {} com sucesso! Orçamento atualizado é de R${}", os.getId(), os.getOrcamento());

                return os;
            }
        }

        return null;
    }

    protected List<Servico> criarServicoParaAdicionarNaOrdemDeServico(OrdemDeServico os, List<Long> idTipoServicosParaAdicionar) {
        List<Long> idsTipoServicoDistintos = idTipoServicosParaAdicionar.stream().distinct().toList();
        Map<Long, TipoServico> tipoServicoPorId = this.tipoServicoGateway.findAllById(idsTipoServicoDistintos).stream().collect(Collectors.toMap(TipoServico::getId, tipoServico -> tipoServico));

        List<TipoServico> tipoServicos = idTipoServicosParaAdicionar.stream()
                .map(tipoServicoPorId::get)
                .filter(Objects::nonNull)
                .toList();

        List<Servico> servicos = new ArrayList<>();

        tipoServicos.forEach(tipoServico -> {
            Servico servico = Servico.criar(tipoServico, os);
            servico.setStatus(ServicoStatus.AGUARDANDO_INICIO);
            servicos.add(servico);
        });

        return servicos;
    }

    @Override
    public OrdemDeServico deleteServicosDaOrdemDeServico(Long id, List<Long> idTipoServicosParaRemover) {
        Optional<OrdemDeServico> osOpt = this.osGateway.findOrdemDeServicoById(id);

        if (osOpt.isPresent() && alteracaoDeServicosOuPecasEPermitida(osOpt.get().getStatus()) && idTipoServicosParaRemover != null && !idTipoServicosParaRemover.isEmpty()) {
            OrdemDeServico os = osOpt.get();
            List<Servico> servicosAtuais = os.getServicos();

            if (servicosAtuais != null && !servicosAtuais.isEmpty()) {
                servicosAtuais.removeIf(servico -> idTipoServicosParaRemover.contains(servico.getTipoServico().getId()));
                os.calcularOrcamento();
                this.osGateway.updateOrdemDeServico(os);

                log.info("Serviços removidos da OS {} com sucesso! Orçamento atualizado é de R${}", id, os.getOrcamento());

                return os;
            }
        }

        return null;
    }

    @Override
    @Transactional
    public OrdemDeServico putPecasNaOrdemDeServico(Long id, List<AdicionarPecaItemInputData> addPecaItemsInputData) {
        Optional<OrdemDeServico> osOpt = this.osGateway.findOrdemDeServicoById(id);

        if (osOpt.isPresent()) {
            return adicionarPecasNaOrdemDeServico(osOpt.get(), addPecaItemsInputData);
        }

        return null;
    }

    @Transactional
    protected OrdemDeServico adicionarPecasNaOrdemDeServico(OrdemDeServico os, List<AdicionarPecaItemInputData> addPecaItemsInputData) {
        if (os != null && alteracaoDeServicosOuPecasEPermitida(os.getStatus()) && addPecaItemsInputData != null && !addPecaItemsInputData.isEmpty()) {
            List<Peca> pecasDaOs = os.getPecas();
            List<Peca> pecasParaAdd = criarPecasParaAdicionarNaOrdemDeServico(addPecaItemsInputData);

            if (pecasParaAdd != null && !pecasParaAdd.isEmpty()) {

                // Inicializa lista de peças da OS se necessário
                if (pecasDaOs == null) {
                    pecasDaOs = new ArrayList<>();
                    os.setPecas(pecasDaOs);
                }

                List<Peca> finalPecasDaOs = pecasDaOs;

                pecasParaAdd.forEach(pecaAdd -> {
                    var idTipoPecaParaAdd = pecaAdd.getTipoPeca().getId();
                    var quantidadeParaAdd = pecaAdd.getQuantidade();

                    // Verifica se peça já existe na Ordem de Serviço
                    var tipoPecaJaExiste = finalPecasDaOs.stream()
                            .filter(pf -> pf.getTipoPeca().getId().equals(idTipoPecaParaAdd))
                            .findFirst();

                    // Caso peça já exista, só adicionamos a quantidade requisitada
                    if (tipoPecaJaExiste.isPresent()) {
                        Peca peca = tipoPecaJaExiste.get();
                        peca.setQuantidade(peca.getQuantidade() + quantidadeParaAdd);
                    } else {
                        finalPecasDaOs.add(pecaAdd);
                    }
                });
                os.calcularOrcamento();
                os = this.osGateway.updateOrdemDeServico(os);

                log.info("Peças adicionadas à OS {} com sucesso! Orçamento atualizado é de R${}", os.getId(), os.getOrcamento());

                return os;
            }
        }

        return null;
    }

    @Transactional
    protected List<Peca> criarPecasParaAdicionarNaOrdemDeServico(List<AdicionarPecaItemInputData> addPecaItemsInputData) {
        List<TipoPeca> tipoPecas = this.tipoPecaGateway.findAllByIds(addPecaItemsInputData.stream().map(AdicionarPecaItemInputData::getIdTipoPeca).toList());

        if (tipoPecas != null && !tipoPecas.isEmpty()) {
            Map<Long, Integer> quantidadeParaAddPorTipoPecaId = addPecaItemsInputData.stream().collect(Collectors.toMap(AdicionarPecaItemInputData::getIdTipoPeca, AdicionarPecaItemInputData::getQuantidade));
            List<Peca> pecas = new ArrayList<>();

            tipoPecas.forEach(tipoPeca -> {
                var idTipoPecaParaAdd = tipoPeca.getId();
                var quantidadeParaAdd = quantidadeParaAddPorTipoPecaId.get(idTipoPecaParaAdd);

                if (tipoPeca.getQuantidadeEstoque() < quantidadeParaAdd) {
                    throw new BadRequestException("Erro: O tipo peça id '%d' não possui a quantidade necessária em estoque! Requisitado: '%d', disponível: '%d'".formatted(tipoPeca.getId(), quantidadeParaAdd, tipoPeca.getQuantidadeEstoque()));
                }

                Peca peca = new Peca();
                peca.setTipoPeca(tipoPeca);
                peca.setQuantidade(quantidadeParaAdd);
                pecas.add(peca);

                tipoPeca.setQuantidadeEstoque(tipoPeca.getQuantidadeEstoque() - quantidadeParaAdd);
            });

            this.tipoPecaGateway.saveAll(tipoPecas);
            return pecas;
        }

        return null;
    }

    @Transactional
    @Override
    public OrdemDeServico deletePecasDaOrdemDeServico(Long id, List<Long> idTipoPecasParaRemover) {
        Optional<OrdemDeServico> osOpt = this.osGateway.findOrdemDeServicoById(id);

        if (osOpt.isPresent() && alteracaoDeServicosOuPecasEPermitida(osOpt.get().getStatus()) && idTipoPecasParaRemover != null && !idTipoPecasParaRemover.isEmpty()) {
            OrdemDeServico os = osOpt.get();
            List<Peca> pecas = os.getPecas();
            List<TipoPeca> tipoPecas = pecas.stream().map(Peca::getTipoPeca).collect(Collectors.toList());

            if (pecas != null && !pecas.isEmpty()) {

                // Adicionando a quantidade de volta no estoque
                for (TipoPeca tp : tipoPecas) {
                    var pecaOpt = pecas.stream().filter(p -> p.getTipoPeca().getId().equals(tp.getId()) && idTipoPecasParaRemover.contains(p.getTipoPeca().getId())).findFirst();
                    pecaOpt.ifPresent(peca -> tp.setQuantidadeEstoque(tp.getQuantidadeEstoque() + peca.getQuantidade()));
                }

                pecas.removeIf(peca -> idTipoPecasParaRemover.contains(peca.getTipoPeca().getId()));

                os.calcularOrcamento();
                this.osGateway.updateOrdemDeServico(os);
                this.tipoPecaGateway.saveAll(tipoPecas);
                log.info("Peças removidas da OS {} com sucesso! Orçamento atualizado é de R${}", id, os.getOrcamento());

                return os;
            }
        }

        return null;
    }

    private String getMensagemOsNaoExiste(Long id) {
        return OS_NAO_EXISTE_MSG_TEMPLATE.formatted(id);
    }
}
