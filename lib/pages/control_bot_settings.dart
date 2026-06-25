import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../l10n/generated/app_localizations.dart';
import '../state.dart';
import '../service.dart';

class ControlBotSection extends StatefulWidget {
  const ControlBotSection({super.key});

  @override
  State<ControlBotSection> createState() => _ControlBotSectionState();
}

class _ControlBotSectionState extends State<ControlBotSection> {
  late TextEditingController _tokenController;
  late TextEditingController _apiUrlController;
  late TextEditingController _chatIdController;

  bool _enabled = false;
  bool _isChanged = false;
  bool? _saveResult;
  String? _testResult;
  bool _isTesting = false;
  bool _hasSmsPermission = true;

  @override
  void initState() {
    super.initState();
    final appState = context.read<AppState>();
    _enabled = appState.controlBotEnabled;
    _tokenController = TextEditingController(
      text: (appState.controlBotConfig['token'] as String?) ?? '',
    );
    _apiUrlController = TextEditingController(
      text: (appState.controlBotConfig['apiUrl'] as String?) ?? '',
    );
    _chatIdController = TextEditingController(
      text: (appState.controlBotConfig['chatId'] as String?) ?? '',
    );
    _checkSmsPermission();
  }

  @override
  void dispose() {
    _tokenController.dispose();
    _apiUrlController.dispose();
    _chatIdController.dispose();
    super.dispose();
  }

  Future<void> _checkSmsPermission() async {
    final granted = await getSmsPermission();
    if (mounted) setState(() => _hasSmsPermission = granted);
  }

  void _onChanged() {
    setState(() {
      _saveResult = null;
      _testResult = null;
      _isChanged = true;
    });
  }

  Future<void> _testConnection() async {
    setState(() { _isTesting = true; _testResult = null; });
    FocusManager.instance.primaryFocus?.unfocus();

    final result = await getUpdates(
      _tokenController.text.trim(),
      _apiUrlController.text.trim(),
    );

    if (!mounted) return;
    if (result.isSuccess && result.data != null) {
      setState(() {
        _chatIdController.text = result.data!;
        _isTesting = false;
        _testResult = 'ok';
        _isChanged = true;
      });
    } else {
      setState(() {
        _isTesting = false;
        _testResult = result.code;
      });
    }
  }

  Future<void> _save() async {
    FocusManager.instance.primaryFocus?.unfocus();
    final appState = context.read<AppState>();

    // Request SEND_SMS permission when enabling
    if (_enabled) {
      final granted = await getSmsPermission(openSettings: true);
      if (mounted) setState(() => _hasSmsPermission = granted);
    }

    final result = await appState.updateControlBot(
      enabled: _enabled,
      config: {
        'token': _tokenController.text.trim(),
        'chatId': _chatIdController.text.trim(),
        'apiUrl': _apiUrlController.text.trim(),
      },
    );

    if (mounted) {
      setState(() {
        _saveResult = result.isSuccess;
        _isChanged = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
          child: Text(l10n.twoWay, style: theme.textTheme.titleSmall),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
          child: Text(l10n.twoWay_help,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              )),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
          child: TextField(
            controller: _tokenController,
            onChanged: (_) => _onChanged(),
            obscureText: true,
            decoration: InputDecoration(
              labelText: l10n.tbot_token,
              border: const OutlineInputBorder(),
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
          child: TextField(
            controller: _apiUrlController,
            onChanged: (_) => _onChanged(),
            decoration: InputDecoration(
              labelText: l10n.tbot_apiUrl,
              hintText: l10n.tbot_apiUrlInfo,
              border: const OutlineInputBorder(),
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
          child: TextField(
            controller: _chatIdController,
            onChanged: (_) => _onChanged(),
            decoration: InputDecoration(
              labelText: l10n.tbot_chatId,
              hintText: l10n.tbot_chatIdInfo,
              border: const OutlineInputBorder(),
            ),
          ),
        ),
        if (_testResult != null && _testResult != 'ok')
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 0),
            child: Text(
              getLocalizedError(l10n, _testResult!, 'telegram_bot'),
              style: TextStyle(color: theme.colorScheme.error, fontSize: 12),
            ),
          ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          child: Row(
            children: [
              OutlinedButton(
                onPressed: _isTesting ? null : _testConnection,
                child: _isTesting
                    ? const SizedBox(width: 16, height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2))
                    : Text(l10n.action_test),
              ),
              const SizedBox(width: 12),
              FilledButton(
                onPressed: _isChanged ? _save : null,
                child: Text(l10n.action_save),
              ),
              if (_saveResult == true) ...[
                const SizedBox(width: 8),
                Icon(Icons.check, color: theme.colorScheme.primary, size: 20),
              ],
            ],
          ),
        ),
        SwitchListTile(
          title: Text(l10n.twoWay_enable),
          value: _enabled,
          onChanged: (val) {
            setState(() { _enabled = val; _isChanged = true; _saveResult = null; });
          },
          contentPadding: const EdgeInsets.fromLTRB(13, 0, 10, 0),
        ),
        if (_enabled && !_hasSmsPermission)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            child: Text(
              l10n.twoWay_permissionWarning,
              style: TextStyle(color: theme.colorScheme.error, fontSize: 12),
            ),
          ),
      ],
    );
  }
}
